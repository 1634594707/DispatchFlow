// 读侧前后对比（路线图 §16.3 / §16.8）：同一台后端、同一份数据，两种读法并排量。
//
//   整园读法 = GET /park/orders + GET /park/vehicles（移动页 2026-09-26 之前的做法，大屏现在仍在用）
//   聚合读法 = GET /park/track（一单 + 那台车 + 最近几单的精简行）
//
// 目的不是"哪个 p95 小"，而是把**每次轮询搬多少字节**这件事变成可复验的数：
// 之前那个 209 KB 是从一次预压的总流量除以请求数推的，这一轮直接按请求记字节。
// 用法（配合 tmp/perf/track-compare-run.sh）：MODE 无关，两个场景同时跑同样多的 VU。
import http from 'k6/http'
import { sleep } from 'k6'
import { Trend } from 'k6/metrics'

const BASE = String(__ENV.TARGET_BASE_URL || 'http://127.0.0.1:8090').replace(/\/+$/, '')
const PARK_ID = __ENV.PARK_ID || '1'
const VUS = Number(__ENV.COMPARE_VUS || 20)
const RAMP_S = Number(__ENV.RAMP_S || 15)
const HOLD_S = Number(__ENV.HOLD_S || 45)

const wholeParkBytes = new Trend('whole_park_bytes', false)
const aggregateBytes = new Trend('aggregate_bytes', false)

const stages = [
  { duration: `${RAMP_S}s`, target: VUS },
  { duration: `${HOLD_S}s`, target: VUS },
  { duration: '15s', target: 0 },
]

export const options = {
  scenarios: {
    whole_park: { executor: 'ramping-vus', startVUs: 1, stages, exec: 'wholeParkPoll' },
    aggregate: { executor: 'ramping-vus', startVUs: 1, stages, exec: 'aggregatePoll' },
  },
  thresholds: {
    // 预算是按 2026-09-26 实测定的：聚合读 avg 4.5 KB / max 8.6 KB，整园读 avg 211 KB。
    // 16 KB 留了一倍余量 —— 谁把整园车队或整园订单塞回这一条响应，这里立刻红。
    aggregate_bytes: ['avg<16384'],
    whole_park_bytes: ['avg>16384'],
    'http_req_duration{name:aggregate_poll}': ['p(95)<400'],
    'http_req_duration{name:whole_park_poll}': ['p(95)<400'],
  },
}

function bytesOf(response) {
  return (response && response.body ? response.body.length : 0) || 0
}

export function wholeParkPoll() {
  const orders = http.get(`${BASE}/api/admin/park/orders?parkId=${PARK_ID}`, {
    tags: { name: 'whole_park_poll' },
  })
  const vehicles = http.get(`${BASE}/api/admin/park/vehicles?parkId=${PARK_ID}`, {
    tags: { name: 'whole_park_poll' },
  })
  wholeParkBytes.add(bytesOf(orders) + bytesOf(vehicles))
  sleep(1.5)
}

export function aggregatePoll() {
  const track = http.get(`${BASE}/api/admin/park/track?parkId=${PARK_ID}&recentLimit=8`, {
    tags: { name: 'aggregate_poll' },
  })
  aggregateBytes.add(bytesOf(track))
  sleep(1.5)
}
