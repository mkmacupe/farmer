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

for (const roleCase of ROLE_CASES) {
  test(`${roleCase.username} page has no critical accessibility violations`, async ({ page }) => {
    await installApiMock(page, { fixedNow: FIXED_NOW });
    await loginAs(page, roleCase.username);
    await expect(page.getByRole('heading', { name: roleCase.heading })).toBeVisible();

    const results = await new AxeBuilder({ page }).analyze();
    const criticalViolations = results.violations.filter((item) => item.impact === 'critical');
    const details = criticalViolations.map((item) => `${item.id}: ${item.help}`).join('\n');

    expect(criticalViolations, details || 'No critical accessibility violations').toEqual([]);
  });
}
