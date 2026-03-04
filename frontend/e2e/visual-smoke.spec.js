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
const HOME_TAB_BY_USER = {
  director: /профиль/i,
  manager: /сводка/i,
  logistician: /назначения/i,
  driver1: /доставки/i
};

async function waitForWorkspaceReady(page) {
  const loadingPlaceholder = page.getByText(/загружаем рабочее пространство/i);
  if (await loadingPlaceholder.count()) {
    await expect(loadingPlaceholder).toBeHidden({ timeout: 15_000 });
  }
}

async function openRoleHomeSection(page, username) {
  const homePattern = HOME_TAB_BY_USER[username];
  if (!homePattern) {
    return;
  }
  const tabButton = page.getByRole('button', { name: homePattern }).first();
  if (await tabButton.count()) {
    await tabButton.click();
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
    await openRoleHomeSection(page, roleCase.username);
    await expect(page.getByRole('heading', { name: roleCase.heading }).first()).toBeVisible();

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
