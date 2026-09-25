import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

/**
 * 深色浮层组件的 token 契约守卫。
 *
 * 起因：移动端亮色页在 `ParkOrder.vue` 里重定义了 `--fsd-text-primary/secondary/tertiary/muted`、
 * `--fsd-bg-hover` 与强调/语义色（accent、accent-strong、error），而 `AmapGeoMap.vue` 的
 * marker 标签、L0/L1/L2 与图层面板都画在**永远深色**的 overlay 上并直接吃这些通用名
 * ⇒ 深字压深底：38 个 marker 标签全部隐形，层级按钮对比度只剩 ~2.9:1，
 * 选中档的 L1试点 只有 2.30:1。修法是在组件根上把这几个名字钉回暗色档。
 *
 * 为什么用源码断言而不是浏览器实测：marker 标签的 DOM 由高德 JSAPI 注入，
 * 而 e2e 为了稳定会 abort `*.amap.com`（见 v14 的 seedMobilePage），CI 里根本不存在
 * `.amap-marker-label` 这个节点，测不到。真实渲染下的对比度是手工量过的（本机与生产一致）：
 * 标签 15.5:1、未选中层级按钮 7.7:1、选中档 7.6:1。
 * 第二条测试把"钉回去的那些色值"本身按 WCAG 公式重算，防止哪天把 on-overlay 改成
 * 一个看着顺眼但过不了 AA 的值。这条守卫保证"钉 token 那段话不会被悄悄删掉，且钉的值够用"。
 */
// 这些是 ESM spec，没有 __dirname ⇒ 先取 dirname 再上两级到 front/（CI 是 Node 20，别用 import.meta.dirname）。
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const read = (p: string) => readFileSync(resolve(ROOT, p), 'utf8')

test('AmapGeoMap pins overlay text tokens at its component root', () => {
  const css = read('src/components/map/AmapGeoMap.vue')
  const block = css.slice(css.indexOf('.amap-geo-map {'))

  for (const name of ['primary', 'secondary', 'tertiary', 'muted']) {
    expect(
      block,
      `--fsd-text-${name} 必须在 .amap-geo-map 根上钉回暗色档，否则移动端亮色页会把它渗进深色浮层`,
    ).toMatch(new RegExp(`--fsd-text-${name}:\\s*var\\(--fsd-text-on-overlay`))
  }
  // 强调色与语义色是同一族渗漏：选中档的 L1试点 吃 --fsd-accent-strong，移动端把它改成了
  // #326f78 ⇒ 落在深色浮层上只剩 2.30:1；--fsd-accent 4.69:1、--fsd-error 4.11:1。
  for (const name of ['accent', 'accent-strong', 'error']) {
    expect(
      block,
      `--fsd-${name} 必须在 .amap-geo-map 根上钉回暗色档`,
    ).toMatch(new RegExp(`--fsd-${name}:\\s*var\\(--fsd-${name}-on-overlay\\)`))
  }
  // 悬停底色同理：移动端把 --fsd-bg-hover 改成了浅色，浅字压浅底。
  expect(block, '--fsd-bg-hover 必须钉到深色档').toMatch(/--fsd-bg-hover:\s*var\(--fsd-surface-hover\)/)
})

test('on-overlay colors clear WCAG AA on the dark overlay', () => {
  const tokens = read('src/styles/tokens.css')
  const hex = (name: string) => {
    const m = tokens.match(new RegExp(`--fsd-${name}:\\s*(#[0-9a-f]{6})`))
    expect(m, `tokens.css 里找不到 --fsd-${name} 的十六进制值`).toBeTruthy()
    return m![1]
  }
  const toRgb = (h: string) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))
  const relLum = (c: number[]) => {
    const [r, g, b] = c.map((v) => {
      const s = v / 255
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }
  const ratio = (fg: string, bg: string) => {
    const a = relLum(toRgb(fg))
    const x = relLum(toRgb(bg))
    return +(((Math.max(a, x) + 0.05) / (Math.min(a, x) + 0.05))).toFixed(2)
  }
  const blendOntoOverlay = (h: string, alpha: number) => {
    const fg = toRgb(h)
    const bg = toRgb(overlay)
    return `#${fg
      .map((v, i) => Math.round(v * alpha + bg[i] * (1 - alpha)).toString(16).padStart(2, '0'))
      .join('')}`
  }

  const overlay = hex('surface-overlay')
  // 选中档的背景是 --fsd-accent-selected（ rgba(86,185,200,.12)）叠在浮层上，按最坏情况取 .16。
  const tinted = blendOntoOverlay(hex('accent'), 0.16)

  // tertiary/muted 是主题里刻意保留的"安静档"（PC 深色页同样只用它们做次要标注），
  // 这里不替主题改它们的下限，只要求可读的那几档过 AA。
  for (const name of [
    'text-on-overlay',
    'text-on-overlay-secondary',
    'accent-on-overlay',
    'accent-strong-on-overlay',
    'error-on-overlay',
  ]) {
    const value = hex(name)
    expect(ratio(value, overlay), `--fsd-${name} ${value} 落在 ${overlay} 上只有`).toBeGreaterThanOrEqual(4.5)
    expect(ratio(value, tinted), `--fsd-${name} ${value} 落在选中档底色 ${tinted} 上只有`).toBeGreaterThanOrEqual(4.5)
  }
})

test('on-overlay text tokens exist in tokens.css', () => {
  const tokens = read('src/styles/tokens.css')
  for (const name of ['', '-secondary', '-tertiary', '-muted']) {
    expect(tokens, `缺少 --fsd-text-on-overlay${name}`).toContain(`--fsd-text-on-overlay${name}:`)
  }
})

test('the light mobile page still re-themes the shared tokens (guard premise)', () => {
  // 这条不是"证明没问题"，而是钉住前提：一旦哪天移动端不再局部重定主题，
  // 上面那条钉 token 的守卫就该重新评估，而不是让它悄悄烂在那儿。
  const mobile = read('src/views/mobile/ParkOrder.vue')
  expect(mobile).toMatch(/--fsd-text-primary:\s*#1a1a1a/)
})
