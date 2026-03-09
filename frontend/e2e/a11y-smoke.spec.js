import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { installApiMock, loginAs } from './helpers/mockApi.js';

const FIXED_NOW = '2026-01-15T10:00:00.000Z';

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

for (const roleCase of ROLE_CASES) {
  test(`${roleCase.username} page has no blocking accessibility violations`, async ({ page }) => {
    await installApiMock(page, { fixedNow: FIXED_NOW });
    await loginAs(page, roleCase.username);
    await waitForWorkspaceReady(page);
    await openRoleHomeSection(page, roleCase.username);
    await expect
      .poll(async () => {
        await openRoleHomeSection(page, roleCase.username);
        try {
          return await page.getByRole('heading', { name: roleCase.heading }).first().isVisible();
        } catch {
          return false;
        }
      }, { timeout: 15_000 })
      .toBe(true);

    const results = await new AxeBuilder({ page }).analyze();
    const blockingImpacts = new Set(['critical', 'serious', 'moderate']);
    const blockingViolations = results.violations.filter((item) => blockingImpacts.has(item.impact));
    const details = blockingViolations.map((item) => `${item.id}: ${item.help}`).join('\n');

    expect(blockingViolations, details || 'No blocking accessibility violations').toEqual([]);
  });
}
