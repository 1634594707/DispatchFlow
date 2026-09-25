// M0 / T0-a 演示倍速两档实测：下单→送达墙钟 + tick 节拍 + 3 s 轮询观感
//
//   node scripts/dev/m0-tick-gear-test.mjs <tick-ms> <pickupStationId> <dropoffStationId> [outDir]
//
// 三个观测项来自 T0-a 的要求：
//   ① 下单→送达墙钟时长      —— 轮询订单状态直到终态（或 120 s 无变化判停）
//   ② 3 s 轮询跳变是否连贯    —— 按前端 3 s 粒度重采样被指派车的像素坐标
//   ③ tick 线程是否追得上      —— 位置每 tick 变一次（步长 8 px），相邻变更的中位间隔＝实际 tick 周期
//
// ③ 用 fixedDelay 语义解读：节拍不会"追赶"，只会把周期拉长成 delay + 单轮耗时。
// 坐标只读接口返回的现成像素值，不在这里做任何像素↔GCJ 换算（SIM 行坐标语义逐行契约）。
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const TICK = Number(process.argv[2])
const OUT_DIR = process.argv[3] || 'tmp/m0'
const BASE = process.env.M0_BASE_URL || 'http://127.0.0.1:8080/api'
const API_KEY = process.env.M0_MOBILE_KEY || 'ZJF-MOBILE-DEMO-2026'
const MAX_MS = Number(process.env.M0_MAX_MS || 900_000)
// 停顿判定要留够余量：一趟 5.3 km 直线的单子在一档里就要跑两百多秒，
// 把 STALL 设成 120 s 会在车还在路上时就把测量提前收掉（实测踩过，报成"未达终态"）。
const STALL_MS = Number(process.env.M0_STALL_MS || 420_000)
const SAMPLE_MS = Number(process.env.M0_SAMPLE_MS || 20)
const POLL_INTERVAL_MS = 250
const TERMINAL = new Set(['DELIVERED', 'COMPLETED', 'CLOSED', 'FINISHED', 'CANCELLED', 'FAILED', 'SIGNED'])

if (!TICK) {
  console.error('usage: node m0-tick-gear-test.mjs <tick-ms> [pickupStationId] [dropoffStationId] [outDir]')
  process.exit(2)
}

mkdirSync(OUT_DIR, { recursive: true })
const t0 = Date.now()
const at = () => Date.now() - t0
const vehicles = new Map()
const orderTimeline = []
let assignedCode = null
let sawPositionMotion = 0
let stopped = false

function noteOrder(status) {
  if (orderTimeline.length && orderTimeline[orderTimeline.length - 1].status === status) return
  orderTimeline.push({ tMs: at(), status })
}

async function api(path, init) {
  const r = await fetch(`${BASE}${path}`, init)
  const body = await r.text()
  let json = null
  try {
    json = JSON.parse(body)
  } catch {
    /* 非 JSON 响应原样带回 */
  }
  return { ok: r.ok, status: r.status, json, body }
}

async function sampleVehicles() {
  const res = await api('/admin/park/vehicles')
  if (!res.ok || !res.json?.data) return
  const t = at()
  for (const v of res.json.data) {
    let s = vehicles.get(v.vehicleCode)
    if (!s) {
      s = { pos: [], soc: [], prevX: null, prevY: null, prevSoc: null, pathLenPx: 0 }
      vehicles.set(v.vehicleCode, s)
    }
    if (s.prevX !== v.x || s.prevY !== v.y) {
      if (s.prevX !== null) {
        s.pathLenPx += Math.hypot(v.x - s.prevX, v.y - s.prevY)
        sawPositionMotion++
      }
      s.pos.push({ t, x: v.x, y: v.y, stage: v.runtimeStage, soc: v.batteryLevel })
      s.prevX = v.x
      s.prevY = v.y
    }
    if (s.prevSoc !== v.batteryLevel) {
      s.soc.push({ t, soc: v.batteryLevel, stage: v.runtimeStage, charging: !!v.charging })
      s.prevSoc = v.batteryLevel
    }
    if (assignedCode === null && v.currentOrderId != null) assignedCode = v.vehicleCode
  }
}

function median(xs) {
  if (!xs.length) return null
  const a = [...xs].sort((p, q) => p - q)
  return a[Math.floor(a.length / 2)]
}

function pctl(xs, p) {
  if (!xs.length) return null
  const a = [...xs].sort((q, r) => q - r)
  return a[Math.min(a.length - 1, Math.floor(a.length * p))]
}

function deltas(points) {
  const out = []
  for (let i = 1; i < points.length; i++) out.push(points[i].t - points[i - 1].t)
  return out
}

// 按前端真实观察粒度 3 s 重采样：位移应≈(3000/tick)*8 px 且方向连贯（不回撤、不瞬移）。
function resample3s(pos) {
  if (pos.length < 2) return null
  const out = []
  let i = 0
  for (let mark = pos[0].t + 3000; mark <= pos[pos.length - 1].t; mark += 3000) {
    while (i + 1 < pos.length && pos[i + 1].t <= mark) i++
    out.push({ t: mark, x: pos[i].x, y: pos[i].y })
  }
  const steps = []
  for (let k = 1; k < out.length; k++) {
    steps.push(Math.hypot(out[k].x - out[k - 1].x, out[k].y - out[k - 1].y))
  }
  return {
    samples: out.length,
    steps,
    medianStepPx: median(steps),
    minStepPx: steps.length ? Math.min(...steps) : null,
    maxStepPx: steps.length ? Math.max(...steps) : null,
    expectedPx: null,
  }
}

// 起终点用**任意点坐标**（V64 路径），不用 stationId。
// 原因实测：设施 v2 之后 ACTIVE 设施只剩 1 母港 + 35 柜 + 6 桩，**没有任何 PICKUP/DROPOFF 作业点**，
// 拿 stationId 下单只会得到 PARK_STATION_NOT_FOUND；而柜/桩按既有不变量不许当货的起终点。
// 默认从接口里的 GEO_POINT/MOTHERSHIP 里挑互相最远的一对，保证这一单足够长、量得出节拍。
function haversine(a, b) {
  const R = 6371000
  const toRad = (d) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

async function resolveEndpoints() {
  if (process.env.M0_PICKUP_LNG && process.env.M0_DROPOFF_LNG) {
    return {
      pickup: { lng: Number(process.env.M0_PICKUP_LNG), lat: Number(process.env.M0_PICKUP_LAT) },
      dropoff: { lng: Number(process.env.M0_DROPOFF_LNG), lat: Number(process.env.M0_DROPOFF_LAT) },
      via: 'env',
    }
  }
  const res = await api('/admin/park/stations?parkId=1')
  const list = Array.isArray(res.json?.data) ? res.json.data : []
  const pool = list
    .filter(
      (s) =>
        s.status === 'ACTIVE' &&
        ['GEO_POINT', 'MOTHERSHIP'].includes(s.stationType) &&
        s.coordLng != null &&
        s.coordLat != null,
    )
    .map((s) => ({ code: s.stationCode, lng: Number(s.coordLng), lat: Number(s.coordLat) }))
  if (pool.length < 2) {
    const diag = {
      fatal: 'not-enough-orderable-points',
      tickMs: TICK,
      httpStatus: res.status,
      stationCount: list.length,
      pool,
    }
    writeFileSync(join(OUT_DIR, `gear-${TICK}.json`), JSON.stringify(diag, null, 2))
    console.log(JSON.stringify(diag))
    process.exit(5)
  }
  let best = { a: pool[0], b: pool[1], d: 0 }
  for (let i = 0; i < pool.length; i++) {
    for (let k = i + 1; k < pool.length; k++) {
      const d = haversine(pool[i], pool[k])
      if (d > best.d) best = { a: pool[i], b: pool[k], d }
    }
  }
  return { pickup: best.a, dropoff: best.b, straightLineMeters: Math.round(best.d), via: 'api-farthest-pair' }
}

async function main() {
  const ep = await resolveEndpoints()
  const created = await api('/admin/park/orders', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'X-Mobile-Api-Key': API_KEY },
    body: JSON.stringify({
      idempotencyKey: `m0-gear-${TICK}-${Date.now()}`,
      parkId: 1,
      pickupLng: ep.pickup.lng,
      pickupLat: ep.pickup.lat,
      dropoffLng: ep.dropoff.lng,
      dropoffLat: ep.dropoff.lat,
      priority: 'P2',
      remark: `M0 T0-a tick=${TICK}ms`,
    }),
  })
  if (!created.ok || !created.json?.data?.orderId) {
    writeFileSync(join(OUT_DIR, `gear-${TICK}.json`), JSON.stringify({ tickMs: TICK, fatal: 'order-rejected', response: created.body }, null, 2))
    console.log(JSON.stringify({ tickMs: TICK, fatal: 'order-rejected', response: created.body }))
    process.exit(3)
  }
  const orderId = created.json.data.orderId
  const tCreate = at()
  noteOrder(created.json.data.orderStatus || 'CREATED')

  // 订单快照字段是 `orderStatus`（另有 runtimeStage / taskStatus）——读 `status` 只会拿到 undefined，
  // 于是"状态无变化"被误判成卡死、120 s 就收工。实测踩过一次。
  let lastChange = at()
  let finalStatus = null
  let finalRow = null
  let sawPositionMotion = 0
  while (!stopped && at() < MAX_MS) {
    const before = sawPositionMotion
    await sampleVehicles()
    if (sawPositionMotion !== before) lastChange = at()
    const snap = await api('/admin/park/orders?parkId=1')
    if (snap.ok && Array.isArray(snap.json?.data)) {
      const row = snap.json.data.find((o) => o.orderId === orderId)
      if (row) {
        finalStatus = row.orderStatus
        finalRow = row
        noteOrder(`${row.orderStatus}/${row.runtimeStage || '-'}`)
      }
    }
    if (finalStatus && TERMINAL.has(finalStatus)) break
    if (at() - lastChange > STALL_MS) break
    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS))
  }

  const movers = [...vehicles.entries()]
    .map(([code, s]) => ({ code, n: s.pos.length, s }))
    .filter((e) => e.n > 3)
    .sort((a, b) => b.n - a.n)
  const primary = movers.find((e) => e.code === assignedCode) || movers[0] || null

  const posD = primary ? deltas(primary.s.pos) : []
  const socD = primary ? deltas(primary.s.soc) : []
  const r3 = primary ? resample3s(primary.s.pos) : null
  if (r3) r3.expectedPx = (3000 / TICK) * 8

  // tick 节拍给三个独立估计量：单车"相邻位置变更的中位间隔"会被装卸停留和让行污染
  // （实测 500 ms 档它报出 846 ms），所以不能只报那一个数。
  const unionT = []
  let firstT = Infinity
  let lastT = -Infinity
  let totalPx = 0
  for (const [, s] of vehicles) {
    totalPx += s.pathLenPx
    for (const p of s.pos) {
      unionT.push(p.t)
      if (p.t < firstT) firstT = p.t
      if (p.t > lastT) lastT = p.t
    }
  }
  unionT.sort((a, b) => a - b)
  const unionD = []
  for (let i = 1; i < unionT.length; i++) {
    const d = unionT[i] - unionT[i - 1]
    if (d > 0) unionD.push(d)
  }
  // tick 节拍的判据。**不要用"全车队位移/8px ÷ 墙钟"去推周期**：N 台车同时在动时
  // 那样会把周期算小 N 倍（实测 250 ms 档它给出 173 ms，比配置值还短，物理上不可能）。
  // 可信的是"单车相邻位置变更间隔"，再看它跨档位是否随配置线性变化。
  const movingVehicles = [...vehicles.values()].filter((s) => s.pos.length > 3).length
  const fleetWindowMs = lastT > firstT ? lastT - firstT : null
  const pxPerSecond = fleetWindowMs && totalPx ? +((totalPx / fleetWindowMs) * 1000).toFixed(2) : null

  const iso = (s) => (s ? Date.parse(s) : null)
  const assignMs = iso(finalRow?.assignTime)
  const startMs = iso(finalRow?.startTime)
  const finishMs = iso(finalRow?.finishTime)
  const span = (a, b) => (a != null && b != null ? b - a : null)
  const tEnd = at()

  const report = {
    tickMs: TICK,
    endpoints: ep,
    orderId,
    assignedVehicle: assignedCode,
    finalStatus,
    terminalReached: !!finalStatus && TERMINAL.has(finalStatus),
    orderWallClockMs: tEnd - tCreate,
    orderSegmentsServerClockMs: {
      assignToFinish: span(assignMs, finishMs),
      assignToStart: span(assignMs, startMs),
      startToFinish: span(startMs, finishMs),
    },
    statusTimeline: orderTimeline,
    tickCadence: {
      configuredMs: TICK,
      movingVehicleCount: movingVehicles,
      singleVehicleMedianMs: median(posD),
      singleVehicleP25Ms: pctl(posD, 0.25),
      singleVehicleP95Ms: pctl(posD, 0.95),
      unionGapMedianMs: median(unionD),
      overrunVsConfig: median(posD) ? +(median(posD) / TICK).toFixed(2) : null,
      fleetPxTravelled: Math.round(totalPx),
      fleetWindowMs,
      fleetPxPerSecond: pxPerSecond,
      socStepMedianMs: median(socD),
    },
    poll3sCoherence: r3 && {
      samples: r3.samples,
      expectedPx: r3.expectedPx,
      medianStepPx: r3.medianStepPx,
      p25StepPx: pctl(r3.steps, 0.25),
      p75StepPx: pctl(r3.steps, 0.75),
      minStepPx: r3.steps.length ? Math.min(...r3.steps) : null,
      maxStepPx: r3.steps.length ? Math.max(...r3.steps) : null,
      stationarySamples: r3.steps.filter((x) => x === 0).length,
    },
    lowSocGuard: [...vehicles.values()].some((s) => s.soc.some((p) => p.soc < 30))
      ? 'vehicles-below-30-present'
      : 'none-below-30',
  }
  writeFileSync(join(OUT_DIR, `gear-${TICK}.json`), JSON.stringify({ report, vehicles: [...vehicles.entries()].map(([c, s]) => ({ code: c, pos: s.pos, soc: s.soc, pathLenPx: s.pathLenPx })) }, null, 2))
  console.log(JSON.stringify(report, null, 2))
  process.exit(0)
}

process.on('SIGINT', () => {
  stopped = true
})
main().catch((e) => {
  console.error('FATAL', e)
  process.exit(4)
})
