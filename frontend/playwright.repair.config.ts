import { defineConfig, devices } from '@playwright/test'

const port = Number(process.env.REPAIR_WEB_PORT)
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Dedicated repair web port required')
const label = process.env.REPAIR_BROWSER_LABEL ?? 'd09-live-browser'
if (!/^[a-z][a-z0-9-]{3,63}$/.test(label)) throw new Error('Invalid repair evidence label')

export default defineConfig({
  testDir: './e2e', workers: 1, retries: 0, timeout: 60_000,
  reporter: [['list'], ['json', { outputFile: `../testing/reports/evidence/autonomous-20261014/${label}-playwright.json` }]],
  outputDir: `../testing/reports/evidence/autonomous-20261014/${label}-artifacts`,
  use: { baseURL: `http://127.0.0.1:${port}`, screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: {
    command: `node ./node_modules/vite/bin/vite.js --configLoader native --host 127.0.0.1 --port ${port} --strictPort`,
    url: `http://127.0.0.1:${port}/login`, reuseExistingServer: false, timeout: 60_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
