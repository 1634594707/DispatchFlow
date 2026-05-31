import lighthouse from 'lighthouse'
import * as chromeLauncher from 'chrome-launcher'
import puppeteer from 'puppeteer-core'
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

const BASE_URL = process.env.LH_BASE_URL || 'http://localhost:3000'
const API_BASE = process.env.PERF_API_BASE || 'http://localhost:8080/api'
const TOKEN_KEY = 'fsd_admin_token'
const USER_KEY = 'fsd_admin_user'
const ROUTES = [
  '/workbench',
  '/dashboard',
  '/tasks',
  '/digital-twin',
  '/vehicle-tracking',
]

async function fetchAdminSession() {
  const res = await fetch(`${API_BASE}/admin/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  const json = await res.json()
  if (!json?.data?.token) {
    throw new Error(`login failed: ${JSON.stringify(json)}`)
  }
  return { token: json.data.token, user: json.data.user }
}

async function seedAuth(page, session) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 60_000 })
  await page.evaluate(
    ({ token, user, tokenKey, userKey }) => {
      localStorage.setItem(tokenKey, token)
      localStorage.setItem(userKey, JSON.stringify(user))
    },
    {
      token: session.token,
      user: session.user,
      tokenKey: TOKEN_KEY,
      userKey: USER_KEY,
    },
  )
}

async function run() {
  const session = await fetchAdminSession()
  const chrome = await chromeLauncher.launch({ chromeFlags: ['--headless=new'] })
  const browser = await puppeteer.connect({
    browserURL: `http://127.0.0.1:${chrome.port}`,
    defaultViewport: null,
  })

  const results = []

  try {
    const page = await browser.newPage()
    await seedAuth(page, session)
    await page.close()

    for (const route of ROUTES) {
      const url = `${BASE_URL}${route}`
      const runner = await lighthouse(url, {
        logLevel: 'error',
        output: 'json',
        port: chrome.port,
        onlyCategories: ['performance'],
      })
      const lhr = runner?.lhr
      if (!lhr) continue

      const audit = lhr.audits
      results.push({
        route,
        performanceScore: lhr.categories.performance?.score,
        fcp: audit['first-contentful-paint']?.displayValue,
        lcp: audit['largest-contentful-paint']?.displayValue,
        tti: audit['interactive']?.displayValue,
        cls: audit['cumulative-layout-shift']?.displayValue,
      })
      console.log(`${route}: score=${lhr.categories.performance?.score} FCP=${audit['first-contentful-paint']?.displayValue}`)
    }
  } finally {
    await browser.disconnect()
    await chrome.kill()
  }

  const outDir = resolve(process.cwd(), 'dist/perf')
  mkdirSync(outDir, { recursive: true })
  writeFileSync(resolve(outDir, 'lighthouse-routes.json'), JSON.stringify(results, null, 2))
}

run().catch((err) => {
  console.error(err)
  process.exit(1)
})
