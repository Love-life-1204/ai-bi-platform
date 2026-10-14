// 浏览器 API 代理到本轮真实测试 JVM；不生成模拟响应。
const { chromium, expect } = require('../../frontend/node_modules/@playwright/test');
const { spawn } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');
const root = path.resolve(__dirname, '../..');
const out = path.join(root, 'testing/reports/evidence');

(async () => {
  const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--configLoader', 'native',
    '--host', '127.0.0.1', '--port', '4174', '--strictPort'],
    { cwd: path.join(root, 'frontend'), windowsHide: true, stdio: 'ignore' });
  let browser;
  const responses = [];
  let result;
  try {
    for (let i = 0; i < 60; i++) {
      try { if ((await fetch('http://127.0.0.1:4174/login')).ok) break; } catch {}
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    browser = await chromium.launch();
    const page = await browser.newPage();
    await page.route(/^http:\/\/127\.0\.0\.1:4174\/api\//, async route => {
      const incoming = new URL(route.request().url());
      const response = await route.fetch({ url: process.env.QA_API_BASE_URL + incoming.pathname + incoming.search });
      responses.push({ method: route.request().method(), path: incoming.pathname, status: response.status() });
      await route.fulfill({ response });
    });
    await page.goto('http://127.0.0.1:4174/login');
    await page.getByPlaceholder('name@company.com').fill(process.env.QA_EMAIL);
    await page.getByPlaceholder('请输入登录密码').fill(process.env.QA_PASSWORD);
    await page.getByRole('button', { name: '登录工作台' }).click();
    await expect(page).toHaveURL(/\/workspace$/, { timeout: 12000 });
    result = { status: 'PASS', scenario: '真实 API 登录并建立企业上下文', responses };
  } catch (error) {
    const page = browser?.contexts()[0]?.pages()[0];
    if (page) await page.screenshot({ path: path.join(out, 'live-browser-failure.png'), fullPage: true });
    result = { status: 'FAIL', scenario: '真实 API 登录并建立企业上下文', error: String(error), responses };
  } finally {
    if (browser) await browser.close();
    server.kill();
  }
  fs.writeFileSync(path.join(out, 'live-browser-results.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
  process.exitCode = result.status === 'PASS' ? 0 : 1;
})();
