import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import { installApiMock, loginAs } from './helpers/mockApi.js';

const FIXED_NOW = '2026-01-15T10:00:00.000Z';
const SCREENSHOT_DIR = path.resolve(process.cwd(), '..', 'output', 'playwright', 'screenshots', 'responsive');

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

const SCALE_CASES = [
  { name: 'mobile-small-100', viewport: { width: 320, height: 568 }, zoomPercent: 100 },
  { name: 'mobile-small-150', viewport: { width: 320, height: 568 }, zoomPercent: 150 },
  { name: 'mobile-medium-125', viewport: { width: 360, height: 780 }, zoomPercent: 125 },
  { name: 'mobile-100', viewport: { width: 390, height: 844 }, zoomPercent: 100 },
  { name: 'mobile-125', viewport: { width: 390, height: 844 }, zoomPercent: 125 },
  { name: 'tablet-100', viewport: { width: 768, height: 1024 }, zoomPercent: 100 },
  { name: 'desktop-100', viewport: { width: 1440, height: 900 }, zoomPercent: 100 }
];

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

async function applyPageZoom(page, zoomPercent) {
  const client = await page.context().newCDPSession(page);
  await client.send('Emulation.setPageScaleFactor', {
    pageScaleFactor: zoomPercent / 100
  });
}

test.beforeAll(async () => {
  await mkdir(SCREENSHOT_DIR, { recursive: true });
});

for (const roleCase of ROLE_CASES) {
  for (const scaleCase of SCALE_CASES) {
    test(`${roleCase.username} layout remains usable at ${scaleCase.name}`, async ({ page }) => {
      await page.setViewportSize(scaleCase.viewport);
      await applyPageZoom(page, scaleCase.zoomPercent);
      await installApiMock(page, { fixedNow: FIXED_NOW });
      await loginAs(page, roleCase.username);
      await waitForWorkspaceReady(page);
      await openRoleHomeSection(page, roleCase.username);

      await expect(page.getByRole('heading', { name: roleCase.heading }).first()).toBeVisible();
      await expect(page.getByText(/непредвиденная ошибка сервера/i)).toHaveCount(0);
      await expect(page.getByText(/что-то пошло не так/i)).toHaveCount(0);

      const horizontalOverflow = await page.evaluate(() => {
        const root = document.documentElement;
        return Math.max(0, root.scrollWidth - root.clientWidth);
      });
      expect(horizontalOverflow).toBeLessThanOrEqual(2);

      const screenshotPath = path.join(
        SCREENSHOT_DIR,
        `${roleCase.username}-${scaleCase.name}.png`
      );
      const screenshotBuffer = await page.screenshot({
        path: screenshotPath,
        fullPage: true
      });
      expect(screenshotBuffer.byteLength).toBeGreaterThan(20_000);
    });
  }
}
