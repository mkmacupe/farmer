import { expect, test } from '@playwright/test';
import { installApiMock, loginAs } from './helpers/mockApi.js';

async function waitForWorkspaceReady(page) {
  const loadingPlaceholder = page.getByText(/загружаем рабочее пространство/i);
  if (await loadingPlaceholder.count()) {
    await expect(loadingPlaceholder).toBeHidden({ timeout: 15_000 });
  }
}

test('director can create delivery request', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'director');
  await waitForWorkspaceReady(page);

  // Navigate to Catalog via sidebar (label is "Каталог")
  await page.getByRole('button', { name: /каталог/i }).click();
  // Wait for the catalog section heading
  await expect(page.getByRole('heading', { name: /каталог и корзина/i, level: 6 })).toBeVisible();
  // Add product to cart - the button text is "В корзину" (not already in cart)
  await page.getByRole('button', { name: /^в корзину$/i }).first().click();
  // Select delivery address
  await page.getByLabel(/адрес доставки/i).click();
  await page.getByRole('option', { name: /main store/i }).click();
  // Submit order
  await page.getByRole('button', { name: /отправить заявку/i }).click();

  await expect(page.getByText(/заявка на доставку создана/i)).toBeVisible();
});

test('manager can approve created order', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'manager');
  await waitForWorkspaceReady(page);

  // Manager dashboard heading should be visible
  await expect(page.getByRole('heading', { name: /панель менеджера/i, level: 5 })).toBeVisible();
  // Navigate to orders via sidebar (label is "Заявки")
  await page.getByRole('button', { name: /заявки/i }).first().click();
  // Wait for the orders section heading
  await expect(page.getByRole('heading', { name: /заявки на доставку/i, level: 5 })).toBeVisible();
  // Click approve button on the first CREATED order
  await page.getByRole('button', { name: /одобрить/i }).first().click();
  await expect(page.getByText(/заказ #301 одобрен/i)).toBeVisible();
});

test('logistician can assign driver to approved order', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'logistician');
  await waitForWorkspaceReady(page);

  // Logistician should see the logistics heading
  await expect(page.getByRole('heading', { name: /логистика и назначения/i, level: 5 })).toBeVisible();
  // Find the approved order row (#302) and select a driver
  // The table shows orders with "#302" format
  const approvedOrderRow = page.getByRole('row', { name: /#302/i });
  // Wait for the row to be visible
  await expect(approvedOrderRow).toBeVisible();
  // Click the select dropdown
  await approvedOrderRow.getByRole('combobox').click();
  await page.getByRole('option', { name: /driver one/i }).click();
  // Assign driver
  await approvedOrderRow.getByRole('button', { name: /назначить/i }).click();
  await expect(page.getByText(/водитель назначен/i)).toBeVisible();
});

test('driver can mark assigned order as delivered', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'driver1');
  await waitForWorkspaceReady(page);

  // Driver should see the deliveries heading
  await expect(page.getByRole('heading', { name: /мои доставки/i, level: 5 })).toBeVisible();
  // Mark the order as delivered
  await page.getByRole('button', { name: /отметить доставленным/i }).first().click();
  await expect(page.getByText(/отмечен как доставленный/i)).toBeVisible();
});

test('director can switch sidebar tabs without runtime crash', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'director');
  await waitForWorkspaceReady(page);

  const tabs = ['Профиль', 'Адреса', 'Каталог', 'История'];
  for (const tab of tabs) {
    await page.getByRole('button', { name: tab, exact: true }).click();
    await page.waitForTimeout(300);
    await expect(page.getByText(/что-то пошло не так/i)).toHaveCount(0);
  }
});
