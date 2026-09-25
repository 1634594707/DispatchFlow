#!/usr/bin/env node
/**
 * 后端契约漂移门（§6.4「前端类型由 OpenAPI 生成」的可用形态）。
 *
 *   node scripts/check-api-contract.mjs              # 比对已抓取的 openapi/api-docs.json 与快照
 *   API_DOCS_URL=http://127.0.0.1:8080/api-docs \
 *     node scripts/check-api-contract.mjs --update    # 从在跑的后端重抓并覆盖快照
 *
 * 为什么不是"直接用生成的类型替换手写类型"（实测过，结论是否定的）：
 *   springdoc 不给 DTO 字段标 nullability/required ⇒ 生成的 `VehicleAdminListItemResponse`
 *   每个字段都是 `?: number` 且 `onlineStatus?: string`（Java 枚举退化成 string）。
 *   而手写的 `src/types/vehicle.d.ts` 是 `vehicleCode: string`（必填）+ `onlineStatus: OnlineStatus`（联合类型）。
 *   拿前者替换后者 = **把类型收窄能力换掉**，是倒退。所以这一门只回答一个问题：
 *   "后端 DTO 的字段集变了没有" —— 变了就必须同步改前端手写类型，而不是让它悄悄漂着。
 */
import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SNAPSHOT = path.join(ROOT, 'openapi', 'api-docs.snapshot.json')
const LIVE = path.join(ROOT, 'openapi', 'api-docs.json')
const url = process.env.API_DOCS_URL || 'http://127.0.0.1:8080/api-docs'
const update = process.argv.includes('--update')

async function fetchLive() {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`抓取 ${url} 失败：HTTP ${res.status}（后端要带 SPRINGDOC_API_DOCS_ENABLED=true 启动）`)
  const text = await res.text()
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`${url} 返回的不是 JSON（前 120 字符：${text.slice(0, 120)}）`)
  }
}

/** schema 名 -> 属性名集合；只比属性集，不比描述与格式。 */
function propertySets(doc) {
  const schemas = doc?.components?.schemas
  if (!schemas) throw new Error('文档里没有 components.schemas —— 拒绝把"解析失败"当成"没有漂移"')
  const out = new Map()
  for (const [name, def] of Object.entries(schemas)) {
    const props = def?.properties
    if (!props || typeof props !== 'object') {
      out.set(name, new Set())
      continue
    }
    out.set(name, new Set(Object.keys(props)))
  }
  return out
}

function diffPaths(a, b) {
  const added = [...b.keys()].filter((x) => !a.has(x))
  const removed = [...a.keys()].filter((x) => !b.has(x))
  const changed = []
  for (const [name, props] of b) {
    const before = a.get(name)
    if (!before) continue
    const add = [...props].filter((p) => !before.has(p))
    const del = [...before].filter((p) => !props.has(p))
    if (add.length || del.length) changed.push({ name, add, del })
  }
  return { added, removed, changed }
}

if (update) {
  const live = await fetchLive()
  await writeFile(LIVE, JSON.stringify(live, null, 2) + '\n', 'utf8')
  await writeFile(SNAPSHOT, JSON.stringify(live, null, 2) + '\n', 'utf8')
  console.log(`[OK] 已重抓并更新快照：${Object.keys(live.components.schemas).length} 个 schema`)
  process.exit(0)
}

let live
if (process.env.USE_LIVE === '1') {
  live = await fetchLive()
} else {
  live = JSON.parse(await readFile(LIVE, 'utf8'))
}
const snapshot = JSON.parse(await readFile(SNAPSHOT, 'utf8'))

const a = propertySets(snapshot)
const b = propertySets(live)
const { added, removed, changed } = diffPaths(a, b)

const pathAdded = Object.keys(live.paths ?? {}).filter((p) => !(p in (snapshot.paths ?? {})))
const pathRemoved = Object.keys(snapshot.paths ?? {}).filter((p) => !(p in (live.paths ?? {})))

if (!added.length && !removed.length && !changed.length && !pathAdded.length && !pathRemoved.length) {
  console.log(`[OK] 契约无漂移：${b.size} 个 schema / ${Object.keys(live.paths).length} 个端点与快照一致`)
  process.exit(0)
}

console.error('[FAIL] 后端契约与快照不一致（前端手写类型可能已经对不上真实响应）')
if (pathAdded.length) console.error(`  新增端点 ${pathAdded.length}：${pathAdded.slice(0, 5).join(', ')}`)
if (pathRemoved.length) console.error(`  删除端点 ${pathRemoved.length}：${pathRemoved.slice(0, 5).join(', ')}`)
if (added.length) console.error(`  新增 schema ${added.length}：${added.slice(0, 5).join(', ')}`)
if (removed.length) console.error(`  删除 schema ${removed.length}：${removed.slice(0, 5).join(', ')}`)
for (const c of changed.slice(0, 20)) {
  console.error(`  ${c.name}: +[${c.add.join(',')}] -[${c.del.join(',')}]`)
}
if (changed.length > 20) console.error(`  ...另有 ${changed.length - 20} 个 schema 变更`)
console.error('  处置：改前端手写类型后跑 `npm run api:fetch` 更新快照；类型不要改成生成的全可选形状（见本脚本注释）。')
process.exit(1)
