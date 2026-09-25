#!/usr/bin/env node
/**
 * 死文档链守卫：代码/配置/脚本里指的 `.md` 文件必须真的存在。
 *
 *   node scripts/check-doc-links.mjs            # 全仓扫描
 *   node scripts/check-doc-links.mjs -v         # 连白名单命中一起打印
 *
 * 为什么要这么一道门（2026-09-22）：本仓的文档会被阶段性收敛删除（`docs/` 现在只剩 4 个文件），
 * 而**代码注释与发布脚本里的文件名不会跟着改**。实测代价已经发生了一次：
 * `.github/workflows/release.yml` 有一行 `cp docs/DEPLOYMENT.md "${OUT}/"`，
 * 该文件已不存在，且这一行**没有** `|| true`（同一块里 RELEASE_NOTES 那行就有）⇒
 * 发布打包会直接失败。这类断裂靠人眼 grep 是守不住的。
 *
 * 白名单（允许指向不存在的文件，因为**不可改**或**是历史记录**）：
 *   - `CHANGELOG.md`：历史条目描述的是当时存在的文档，改写历史等于伪造记录。
 *   - `back/sql/migrations/**`：已应用的迁移不可编辑（改一个字节就破坏 Flyway 校验和）。
 */
import { execFileSync } from 'node:child_process'
import { readFileSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const verbose = process.argv.includes('-v')

const WHITELIST = [
  { prefix: 'CHANGELOG.md', why: '历史记录' },
  { prefix: 'back/sql/migrations/', why: '已应用迁移不可编辑' },
  { prefix: 'docs/DispatchFlow_已完成工作记录_', why: '执行记录（历史）' },
]

// 有些引用是**故意**提一个已删除的文件名（"原 docs/X.md 已随文档收敛删除"）。
// 这类句子不是活指针，但也别随手放过整份文件 —— 只按这一行的措辞判，并在 -v 里列出来。
const HISTORICAL_MENTION = /文档收敛删除|已删除|该文档已删除|deleted in the 2026-09-22/

const EXT = /\.(md|java|ts|tsx|vue|js|mjs|cjs|py|sh|ps1|yml|yaml|json|sql|xml)$/i

// docs/ 前缀写法与《书名号》写法都要覆盖；文件名允许中文、数字、下划线、连字符与点。
const PATTERN = /(?:docs[/\\]|《)([A-Za-z0-9_\u4e00-\u9fff][A-Za-z0-9_\u4e00-\u9fff.\-]*\.md)/g

function trackedFiles() {
  const out = execFileSync('git', ['-C', ROOT, 'ls-files', '-z'], { encoding: 'utf8' })
  return out.split('\0').filter(Boolean)
}

function candidates(name) {
  return [
    path.join(ROOT, 'docs', name),
    path.join(ROOT, name),
  ]
}

const problems = []
const whitelisted = []
const checkedRefs = new Set()

for (const rel of trackedFiles()) {
  if (!EXT.test(rel)) continue
  if (/(^|\/)(node_modules|dist|target|build)\//.test(rel.replace(/\\/g, '/'))) continue
  let body
  try {
    body = readFileSync(path.join(ROOT, rel), 'utf8')
  } catch {
    continue
  }
  const inWhitelist = WHITELIST.some(w => rel.startsWith(w.prefix))
  for (const m of body.matchAll(PATTERN)) {
    const name = m[1]
    // 键必须带位置：按 (文件, 文件名) 去重会让"同名的第二处引用"永远逃过检查，
    // 而第一处一旦被白名单放过，后面几处就再也不报错了（实测漏掉过一条）。
    const key = `${rel}@${body.slice(0, m.index).split('\n').length} -> ${name}`
    if (checkedRefs.has(key)) continue
    checkedRefs.add(key)
    if (candidates(name).some(existsSync)) continue
    const lineNo = body.slice(0, m.index).split('\n').length
    const lineText = body.split('\n')[lineNo - 1] ?? ''
    const rec = { file: rel, name, line: lineNo }
    if (HISTORICAL_MENTION.test(lineText)) {
      whitelisted.push({ ...rec, why: '历史提及（同一行写明该文档已删除）' })
    } else if (inWhitelist) {
      whitelisted.push({ ...rec, why: WHITELIST.find(w => rel.startsWith(w.prefix)).why })
    } else {
      problems.push(rec)
    }
  }
}

if (verbose) {
  for (const w of whitelisted) console.log(`[skip] ${w.file}:${w.line} -> ${w.name}  (${w.why})`)
}

if (!problems.length) {
  console.log(`[OK] 文档链完整：检查 ${checkedRefs.size} 条引用，` +
    `白名单放过 ${whitelisted.length} 条（历史记录/已应用迁移）`)
  process.exit(0)
}

console.error(`[FAIL] ${problems.length} 条文档引用指向不存在的文件：`)
for (const p of problems) console.error(`  ${p.file}:${p.line}  ->  docs/${p.name}`)
console.error('处置：改指向仍然存在的文档（docs/ 下现存 4 份），或把内容并进来后删掉引用。')
console.error('     不要恢复已被删除的中间文档。')
process.exit(1)
