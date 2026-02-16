import { expect, test } from '@playwright/test';
import { installApiMock, loginAs } from './helpers/mockApi.js';

test('director can create delivery request', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'director');

  // Navigate to Catalog via sidebar (label is "Каталог")
  await page.getByRole('button', { name: /каталог/i }).click();
  // Wait for the catalog section heading
  await expect(page.getByRole('heading', { name: /каталог и корзина/i })).toBeVisible();
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

  // Manager dashboard heading should be visible
  await expect(page.getByRole('heading', { name: /панель менеджера/i })).toBeVisible();
  // Navigate to orders via sidebar (label is "Заявки")
  await page.getByRole('button', { name: /заявки/i }).first().click();
  // Wait for the orders section heading
  await expect(page.getByRole('heading', { name: /заявки на доставку/i })).toBeVisible();
  // Click approve button on the first CREATED order
  await page.getByRole('button', { name: /одобрить/i }).first().click();
  await expect(page.getByText(/заказ #301 одобрен/i)).toBeVisible();
});

test('logistician can assign driver to approved order', async ({ page }) => {
  await installApiMock(page);
  await loginAs(page, 'logistician');

  // Logistician should see the logistics heading
  await expect(page.getByRole('heading', { name: /логистика и назначения/i })).toBeVisible();
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

  // Driver should see the deliveries heading
  await expect(page.getByRole('heading', { name: /мои доставки/i })).toBeVisible();
  // Mark the order as delivered
  await page.getByRole('button', { name: /отметить доставленным/i }).first().click();
  await expect(page.getByText(/отмечен как доставленный/i)).toBeVisible();
});
