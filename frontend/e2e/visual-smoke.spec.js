import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { installApiMock, loginAs } from './helpers/mockApi.js';

const FIXED_NOW = '2026-01-15T10:00:00.000Z';
const SCREENSHOT_DIR = path.resolve(process.cwd(), '..', 'output', 'playwright', 'screenshots');

const ROLE_CASES = [
  { username: 'director', heading: /закупки без лишних шагов/i },
  { username: 'manager', heading: /панель менеджера/i },
  { username: 'logistician', heading: /логистика и назначения/i },
  { username: 'driver1', heading: /мои доставки/i }
];

async function waitForWorkspaceReady(page) {
  const loadingPlaceholder = page.getByText(/загружаем рабочее пространство/i);
  if (await loadingPlaceholder.count()) {
    await expect(loadingPlaceholder).toBeHidden({ timeout: 15_000 });
  }
}

test.beforeAll(async () => {
  await mkdir(SCREENSHOT_DIR, { recursive: true });
});

for (const roleCase of ROLE_CASES) {
  test(`${roleCase.username} visual smoke screenshot is captured`, async ({ page }) => {
    await installApiMock(page, { fixedNow: FIXED_NOW });
    await loginAs(page, roleCase.username);
    await waitForWorkspaceReady(page);
    await expect(page.getByRole('heading', { name: roleCase.heading, level: 5 })).toBeVisible();

    await page.addStyleTag({
      content: `
        *, *::before, *::after {
          animation: none !important;
          transition: none !important;
          caret-color: transparent !important;
        }
      `
    });
    await page.evaluate(() => window.scrollTo(0, 0));

    const screenshotPath = path.join(SCREENSHOT_DIR, `${roleCase.username}-home.png`);
    const screenshotBuffer = await page.screenshot({
      path: screenshotPath,
      fullPage: true
    });

    expect(screenshotBuffer.byteLength).toBeGreaterThan(20_000);
  });
}
