// DispatchFlow 压测场景（k6 v1）——移动端真实用户旅程，匿名路径（与线上服务器 .env 同配置）。
//
// 打哪些接口（全部是 AdminAuthInterceptor 的"可选鉴权"路径，且 Controller 里那两把匿名开关
// 已关，所以不带任何凭证；带 X-Mobile-Api-Key 会被 MobileOrderAuthServiceImpl 硬顶在
// 30 次/分钟，测出来的平台期是那行代码，不是系统）：
//   POST /api/admin/park/orders          下单（受理→吸附→建任务→派单，写路径全在这里）
//   GET  /api/admin/park/orders          追踪快照（手机页 1.5s 轮询）
//   GET  /api/admin/park/vehicles        车队快照（同上）
//   GET  /api/admin/park/stations        页面载入时的点位（setup 里取一次，不进循环）
//   GET  /api/admin/park/layout          地图底图（页面载入，重读）
//
// 外部依赖：本场景**不该出网打高德**。后端镜像里 FSD_AMAP_DRIVING_ENABLED=false，
// 路线规划落回本地路网图 —— 那是被测对象；开着的话 p95 里混的是第三方延迟和配额。
//
// OD 坐标来自 /scripts/od-pairs.json：由 run-perf.sh 从**同一套 seed 灌出来的库里**现取
// （STANDBY 泊位 + ACTIVE 站点的 coord_lng/coord_lat），所以"能吸附、在围栏内"是由数据保证的，
// 不是手挑的。冷启动库里 GEO_POINT 落点为 0（那是下单时才 materialize 的），因此这里
// 不能拿 GEO_POINT 当来源。
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

// k6 v1 没有 `k6/util` 这个内置模块（jslib 才有），拿它当依赖会在 archive 阶段就炸：
// "unknown dependency : k6/util"。本地实现三行，换来的是不联网也能起 Job。
const randomIntBetween = (min, max) => min + Math.floor(Math.random() * (max - min + 1))

const BASE = String(__ENV.TARGET_BASE_URL || 'http://frontend:80').replace(/\/+$/, '')
const PARK_ID = __ENV.PARK_ID || '1'
const MODE = __ENV.MODE || 'full'          // full=下单+轮询 | poll=只读轮询
const TRACKING_POLLS = Number(__ENV.TRACKING_POLLS || 3)
const ORDER_VUS = Number(__ENV.ORDER_VUS || 30)
const POLL_VUS = Number(__ENV.POLL_VUS || 60)
const RAMP_S = Number(__ENV.RAMP_S || 90)
const HOLD_S = Number(__ENV.HOLD_S || 180)
// 收尾降档长度跟着档位走：定档时它是一段真实的退坡，冒烟时它不该把 25 秒的窗口拖成 30 秒的尾巴
const TAIL_S = Math.max(10, Math.round(HOLD_S / 4))
const POLL_RAMP = Math.max(30, Math.round(RAMP_S / 2))

const PAIRS = JSON.parse(open('./od-pairs.json'))
if (!Array.isArray(PAIRS) || PAIRS.length < 2) {
  throw new Error(`od-pairs.json 只有 ${Array.isArray(PAIRS) ? PAIRS.length : '没读到'} 个可用坐标，场景无效（应 ≥2）`)
}

const orderAccepted = new Rate('order_accepted')      // 受理成功率（业务口径，不是 HTTP 口径）
const orderGeoReject = new Rate('order_geo_reject')   // 拒在范围/吸附 → 是我们的夹具坏了
const orderCapReject = new Rate('order_capacity_reject') // 拒在"没车可派" → 是容量结论，不设门槛
const orderOtherReject = new Rate('order_other_reject')
// 派单命中率（有车可派的比例）：容量结论，不参与门槛
const taskAssigned = new Rate('task_assigned')
// 反证指标：干跑时踩到的第一条假绿 —— 场景函数一抛异常（`__iteration` 这个全局在 k6 里不存在），
// order_flow 一次 POST 都没发出，而 `order_accepted: rate>0.9` 这类**基于 Rate 的阈值在 0 样本时
// 判为通过**，k6 退出码 0、Job 全绿。所以"这轮真的下过单"必须用一个**计数**阈值断言，
// 不能靠"成功率>90%"自己证明自己跑过。
const ordersPosted = new Counter('orders_posted')
// 夹具自身的不变量（OD 池退化到只剩同址点），不是被测系统的指标 —— 见 pickPair()
const pairDegenerate = new Counter('pair_degenerate')
// 一次追踪轮询（orders + vehicles 两个请求）的响应体合计字节数。
// 第二个参数是 isTime，**必须是 false**：写成 true 时 k6 把字节数按毫秒排版，
// 本机预压那轮就打印成了 `avg=3m29s`（其实是 209,000 字节），一眼看去像时间指标坏了。
const pollBytes = new Trend('tracking_poll_bytes', false)

const GEO_CODES = /OUT_OF|SERVICE_AREA|SNAP|GEO|RANGE|FENCE|UNREACHABLE|ENDPOINT/i
const CAP_CODES = /NO_AVAILABLE|CAPACITY|NO_VEHICLE|IDLE/i

const stages = (peak, ramp, hold) => [
  { duration: `${ramp}s`, target: peak },
  { duration: `${hold}s`, target: peak },
  { duration: `${TAIL_S}s`, target: 0 },
]

export const options = {
  noConnectionReuse: false,     // 手机页是一条 keep-alive 连接，别把它测成建连风暴
  insecureSkipTLSVerify: true,
  scenarios: {
    order_flow: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: stages(ORDER_VUS, RAMP_S, HOLD_S),
      exec: 'orderFlow',
    },
    // 只读洪峰晚一步进场：这样"p95 变差"至少能分清是被写入挤的还是被读打爆的
    poll_only: {
      executor: 'ramping-vus',
      startTime: `${RAMP_S}s`,
      startVUs: 0,
      stages: stages(POLL_VUS, POLL_RAMP, HOLD_S),
      exec: 'pollOnly',
    },
  },
  thresholds: Object.assign(
    {
      // ⛔ 本条**现在按预期是红的**：写路径每 15 单左右有一次 HTTP 500（内层事务被标 rollback-only
      //    后又被吞，见路线图 §16.4 / 待办 #16）。红了不是流水线坏，是它抓住了东西 ——
      //    别靠把阈值放宽到 10% 把它调绿。
      'http_req_failed{name:order_create}': ['rate<0.02'],
      'http_req_failed{scenario:order_flow}': ['rate<0.02'],
      'http_req_duration{name:order_create}': ['p(95)<1500'],
      'http_req_duration{name:tracking_poll}': ['p(95)<800'],
      order_geo_reject: ['rate<0.005'],
      order_other_reject: ['rate<0.02'],
      pair_degenerate: ['count==0'],
    },
    // 只有真下单的档位才断言"发过单"。MODE=poll 是纯读档，那里 0 单是设计如此。
    MODE === 'poll' ? {} : { order_accepted: ['rate>0.9'], orders_posted: ['count>=1'] },
  ),
}

/** 先确认"这套凭证模型 + 这个 parkId"真的通，再放流。
 *  setup 里失败 = Job 直接红，比几千个 401 把 p95 摊薄成"看起来没问题"诚实得多。 */
export function setup() {
  const parks = http.get(`${BASE}/api/admin/parks`, { tags: { name: 'bootstrap_parks' } })
  if (parks.status !== 200) {
    throw new Error(`/api/admin/parks → ${parks.status}：前端 nginx 反代或后端没起来，压测无意义`)
  }
  const stations = http.get(`${BASE}/api/admin/park/stations?parkId=${PARK_ID}`, {
    tags: { name: 'bootstrap_stations' },
  })
  const body = stations.body ? JSON.parse(stations.body) : {}
  const active = (body.data || []).filter((s) => s.status === 'ACTIVE')
  if (stations.status !== 200 || active.length === 0) {
    throw new Error(
      `parkId=${PARK_ID} 的 ACTIVE 站点数=${active.length}（HTTP ${stations.status} ${body.code || ''}）—— seed 没灌进去`,
    )
  }
  const layout = http.get(`${BASE}/api/admin/park/layout?parkId=${PARK_ID}`, { tags: { name: 'bootstrap_layout' } })
  if (layout.status !== 200) {
    throw new Error(`/api/admin/park/layout → ${layout.status}`)
  }
  console.log(`入口自检通过：park=${PARK_ID} 站点=${active.length} OD 对来源=${PAIRS.length} 个坐标 MODE=${MODE}`)
  return { parkId: PARK_ID }
}

function pickPair() {
  // 取送两点**不能是同一个坐标**。这里防的不是"a===b 的下标"，而是"两个不同的点恰好同坐标"：
  // 基地那 6 根桩与 6 台柜是**同址**的（seed 里就是同一个点），去重后只剩一个坐标。
  // 干跑实测：同址两点被选成一对 ⇒ 后端按"路网上连不通（0 m / 0 m）"拒 400，
  // 而那是夹具的错，不是被测系统的错 ⇒ 会污染 order_geo_reject。
  for (let attempt = 0; attempt < 8; attempt++) {
    const a = randomIntBetween(0, PAIRS.length - 1)
    let b = randomIntBetween(0, PAIRS.length - 2)
    if (b >= a) b += 1
    const from = PAIRS[a]
    const to = PAIRS[b]
    if (from.lng !== to.lng || from.lat !== to.lat) return [from, to]
  }
  return null
}

function postOrder() {
  const pair = pickPair()
  if (pair === null) {
    // 夹具自己的不变量：OD 池里只剩同址点就会走到这里。它不是"系统拒单"，
    // 所以单独计数、并且用 `count==0` 断言 —— 绝不把它折进 geo_reject 里冒充被测结论。
    pairDegenerate.add(1)
    console.error('OD 池退化成同址点：坐标去重后不够配对，本轮数据不可用')
    return false
  }
  const [from, to] = pair
  const payload = JSON.stringify({
    idempotencyKey: `perf-${__VU}-${__ITER}-${randomIntBetween(100000, 999999)}`,
    parkId: Number(PARK_ID),
    pickupLng: from.lng,
    pickupLat: from.lat,
    dropoffLng: to.lng,
    dropoffLat: to.lat,
    orderPriority: 'NORMAL',
    remark: 'k6 perf',
  })
  const res = http.post(`${BASE}/api/admin/park/orders`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'order_create' },
  })
  ordersPosted.add(1)
  let envelope = {}
  try {
    envelope = res.body ? JSON.parse(res.body) : {}
  } catch (e) {
    envelope = { success: false, code: 'UNPARSEABLE_BODY' }
  }
  const ok = res.status === 200 || res.status === 201
  const accepted = ok && envelope.success === true
  const data = envelope.data || {}
  orderAccepted.add(accepted)
  // 三类拒单的分母都是**每一次下单**，不是"被拒的那些"。写成"只在拒单时 add"会得到 100%
  // （本机预压实测：2 个拒单恰好同一类 ⇒ `order_other_reject rate=100%`，而真实占比是 2/488=0.4%），
  // 门槛于是被一条统计口径炸掉 —— 判据本身错了比数字难看更贵。
  const code = accepted ? '' : String(envelope.code || `HTTP_${res.status}`)
  orderGeoReject.add(!accepted && GEO_CODES.test(code))
  orderCapReject.add(!accepted && CAP_CODES.test(code))
  orderOtherReject.add(!accepted && !GEO_CODES.test(code) && !CAP_CODES.test(code))
  if (!accepted) {
    console.error(`下单被拒 HTTP=${res.status} code=${code} msg=${envelope.message || ''}`)
  }
  // "受理成功"≠"派到车"：派单失败时接口照样返回 success=true，任务落进 MANUAL_PENDING。
  // 本机预压实测 488 单里 99% 的任务最终 MANUAL_PENDING，而 order_accepted 报 99.6% ——
  // 所以派单结果单独量，并且**不设门槛**：它是车队容量结论，不是平台结论。
  taskAssigned.add(accepted && data.vehicleId != null)
  check(res, { 'order http<500': (r) => r.status < 500 })
  return accepted
}

function pollOnce() {
  const r1 = http.get(`${BASE}/api/admin/park/orders?parkId=${PARK_ID}`, { tags: { name: 'tracking_poll' } })
  const r2 = http.get(`${BASE}/api/admin/park/vehicles?parkId=${PARK_ID}`, { tags: { name: 'tracking_poll' } })
  // 轮询变慢的第一嫌疑是**体积**不是 CPU：这两个接口返回的是整园快照（订单 + 车队），
  // 单量堆起来后每次轮询的字节数跟着长。不把体积记成指标，就只能说"p95 变差了"而说不出为什么。
  pollBytes.add((r1.body || '').length + (r2.body || '').length)
  check(r1, { 'poll ok': (r) => r.status === 200 })
  check(r2, { 'poll ok': (r) => r.status === 200 })
}

export function orderFlow() {
  if (MODE !== 'poll') {
    postOrder()
    sleep(randomIntBetween(1, 2))
  }
  for (let i = 0; i < TRACKING_POLLS; i++) {
    pollOnce()
    sleep(1.5)
  }
  sleep(randomIntBetween(1, 3))   // 手机页不是死循环：人是看一眼、再看的
}

/** 纯读洪峰：模拟"大屏/many 用户只看不动"，用来把读扩展性和写争用分开。 */
export function pollOnly() {
  pollOnce()
  sleep(randomIntBetween(1, 3))
}
