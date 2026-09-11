import { chromium } from '@playwright/test'

const browser = await chromium.launch()
const page = await browser.newPage()

const errors = []
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(`[console.error] ${msg.text()}`)
})
page.on('pageerror', (err) => errors.push(`[pageerror] ${err.message}\n${err.stack ?? ''}`))

await page.goto('http://localhost:4173/', { waitUntil: 'networkidle', timeout: 30000 })
await page.waitForTimeout(3000)

const bodyText = await page.evaluate(() => document.body.innerText.slice(0, 500))
const bodyHtml = await page.evaluate(() => document.body.innerHTML.slice(0, 800))

console.log('--- BODY TEXT ---')
console.log(bodyText || '(empty)')
console.log('--- BODY HTML ---')
console.log(bodyHtml || '(empty)')
console.log('--- ERRORS ---')
console.log(errors.length ? errors.join('\n\n') : '(none)')

await browser.close()
