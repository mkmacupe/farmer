import { expect, test } from '@playwright/test';
import { installApiMock, loginAs } from './helpers/mockApi.js';

const FIXED_NOW = '2026-01-15T10:00:00.000Z';

const MOBILE_CASES = [
  { name: 'mobile-small-100', viewport: { width: 320, height: 568 }, zoomPercent: 100 },
  { name: 'mobile-small-125', viewport: { width: 320, height: 568 }, zoomPercent: 125 },
  { name: 'mobile-100', viewport: { width: 390, height: 844 }, zoomPercent: 100 }
];

const ROLE_CASES = [
  {
    username: 'director',
    heading: /закупки без лишних шагов/i,
    tabs: [/профиль/i, /адреса/i, /каталог/i, /история/i]
  },
  {
    username: 'manager',
    heading: /панель менеджера/i,
    tabs: [/сводка/i, /заявки/i, /товары/i, /пользователи/i, /отчёты/i]
  },
  {
    username: 'logistician',
    heading: /логистика и назначения/i,
    tabs: [/назначения/i]
  },
  {
    username: 'driver1',
    heading: /мои доставки/i,
    tabs: [/доставки/i]
  }
];

async function waitForWorkspaceReady(page) {
  const loadingPlaceholder = page.getByText(/загружаем рабочее пространство/i);
  if (await loadingPlaceholder.count()) {
    await expect(loadingPlaceholder).toBeHidden({ timeout: 15_000 });
  }
}

async function applyPageZoom(page, zoomPercent) {
  const client = await page.context().newCDPSession(page);
  await client.send('Emulation.setPageScaleFactor', {
    pageScaleFactor: zoomPercent / 100
  });
}

async function expectNoHorizontalOverflow(page) {
  const horizontalOverflow = await page.evaluate(() => {
    const root = document.documentElement;
    return Math.max(0, root.scrollWidth - root.clientWidth);
  });
  expect(horizontalOverflow).toBeLessThanOrEqual(2);
}

for (const roleCase of ROLE_CASES) {
  for (const mobileCase of MOBILE_CASES) {
    test(`${roleCase.username} mobile tabs remain usable at ${mobileCase.name}`, async ({ page }) => {
      await page.setViewportSize(mobileCase.viewport);
      await applyPageZoom(page, mobileCase.zoomPercent);
      await installApiMock(page, { fixedNow: FIXED_NOW });
      await loginAs(page, roleCase.username);
      await waitForWorkspaceReady(page);
      await expect(page.getByRole('heading', { name: roleCase.heading }).first()).toBeVisible();

      for (const tabPattern of roleCase.tabs) {
        const tabButton = page.getByRole('button', { name: tabPattern }).first();
        await tabButton.scrollIntoViewIfNeeded();
        await expect(tabButton).toBeVisible();
        await tabButton.click();
        await page.waitForTimeout(200);

        await expect(page.getByText(/непредвиденная ошибка сервера/i)).toHaveCount(0);
        await expect(page.getByText(/что-то пошло не так/i)).toHaveCount(0);
        await expectNoHorizontalOverflow(page);
      }
    });
  }
}
