import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'

/**
 * 深色浮层组件的 token 契约守卫。
 *
 * 起因：移动端亮色页在 `ParkOrder.vue` 里重定义了 `--fsd-text-primary/secondary/tertiary/muted`
 * 与 `--fsd-bg-hover`（改成 #1a1a1a/#666/#999/#ccc 与浅色 hover），而 `AmapGeoMap.vue` 的
 * marker 标签、L0/L1/L2 与图层面板都画在**永远深色**的 overlay 上并直接吃这些通用名
 * ⇒ 深字压深底：38 个 marker 标签全部隐形，层级按钮对比度只剩 ~2.9:1。
 * 修法是在组件根上把这几个名字钉回暗色档。
 *
 * 为什么用源码断言而不是浏览器实测：marker 标签的 DOM 由高德 JSAPI 注入，
 * 而 e2e 为了稳定会 abort `*.amap.com`（见 v14 的 seedMobilePage），CI 里根本不存在
 * `.amap-marker-label` 这个节点，测不到。真实渲染下的对比度是手工量过的：
 * 标签 15.5:1、层级按钮 7.7:1。这条守卫只保证"钉 token 那段话不会被悄悄删掉"。
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
  // 悬停底色同理：移动端把 --fsd-bg-hover 改成了浅色，浅字压浅底。
  expect(block, '--fsd-bg-hover 必须钉到深色档').toMatch(/--fsd-bg-hover:\s*var\(--fsd-surface-hover\)/)
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
