import { expect, test } from '@playwright/test';

const ENABLED = process.env.E2E_LIVE_API === '1';
const USERNAME = process.env.E2E_LIVE_USERNAME || 'manager';
const PASSWORD = process.env.E2E_LIVE_PASSWORD || '1';

test.describe('Live API Smoke', () => {
  test.skip(!ENABLED, 'Set E2E_LIVE_API=1 to run against a real backend.');

  test('can authenticate and open workspace with live backend', async ({ page }) => {
    await page.goto('/');

    await page.getByLabel(/логин/i).fill(USERNAME);
    await page.getByLabel(/пароль/i).fill(PASSWORD);
    await page.getByRole('button', { name: /^войти$/i }).click();

    await expect(
      page.getByRole('heading', {
        name: /(сводка|панель менеджера|закупки без лишних шагов|логистика и назначения|назначения|мои доставки)/i
      }).first()
    ).toBeVisible({ timeout: 20_000 });

    await expect(page.getByText(/сервер недоступен/i)).toHaveCount(0);
    await expect(page.getByText(/что-то пошло не так/i)).toHaveCount(0);
  });
});
