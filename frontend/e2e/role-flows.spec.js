import { expect, test } from '@playwright/test';
import { installApiMock, loginAs } from './helpers/mockApi.js';

async function waitForWorkspaceReady(page) {
  const loadingPlaceholder = page.getByText(/загружаем рабочее пространство/i);
  try {
    await expect(loadingPlaceholder).toBeHidden({ timeout: 30_000 });
  } catch (e) {}
  await expect(page.getByRole('button', { name: /выйти/i }).first()).toBeVisible({ timeout: 30_000 });
}

async function selectMuiOption(page, labelRegex, optionTextRegex) {
  const label = page.locator('label').filter({ hasText: labelRegex }).first();
  const selectId = await label.getAttribute('id');
  const select = page.locator(`[aria-labelledby*="${selectId}"]`).first();
  
  await select.click();
  const option = page.getByRole('option', { name: optionTextRegex }).first();
  await expect(option).toBeVisible();
  await option.click();
}

test.describe('Director Flows', () => {
  test.beforeEach(async ({ page }) => {
    await installApiMock(page);
    await loginAs(page, 'director');
    await waitForWorkspaceReady(page);
  });

  test('can update profile', async ({ page }) => {
    await page.getByRole('button', { name: /профиль/i }).first().click();
    await page.waitForTimeout(500);
    
    await page.getByLabel(/фио/i).fill('New Director Name');
    await page.getByLabel(/телефон/i).fill('+380991234567');
    await page.getByTestId('profile-save-button').click();
    
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/профиль/i);
  });

  test('can manage delivery addresses', async ({ page }) => {
    await page.getByRole('button', { name: /адреса/i }).first().click();
    await page.waitForTimeout(500);
    await expect(page.getByLabel(/название точки/i)).toBeVisible();
    await expect(page.locator('.map-picker-canvas, .leaflet-container').first()).toBeVisible();
    await expect(page.getByText(/Main Store/i)).toBeVisible();
  });

  test('can create delivery request from catalog', async ({ page }) => {
    await page.getByRole('button', { name: /каталог/i }).first().click();
    await page.waitForTimeout(500);
    
    await page.getByRole('button', { name: /в корзину/i }).first().click();
    await selectMuiOption(page, /адрес доставки/i, /Main Store/i);
    
    await page.getByRole('button', { name: /отправить заявку/i }).click();
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/заявка/i);
  });

  test('can repeat order from history', async ({ page }) => {
    await page.getByRole('button', { name: /история/i }).first().click();
    await page.waitForTimeout(500);
    
    await page.getByRole('button', { name: /повторить/i }).first().click();
    
    const snack = page.locator('.MuiAlert-message').first();
    try {
        await expect(snack).toContainText(/повторён/i, { timeout: 3000 });
    } catch (e) {
        await selectMuiOption(page, /адрес доставки/i, /Main Store/i);
        await page.getByRole('button', { name: /повторить/i }).first().click();
        await expect(page.locator('.MuiAlert-message').first()).toContainText(/повторён/i);
    }
  });
});

test.describe('Manager Flows', () => {
  test.beforeEach(async ({ page }) => {
    await installApiMock(page);
    await loginAs(page, 'manager');
    await waitForWorkspaceReady(page);
  });

  test('can see dashboard insights', async ({ page }) => {
    await page.getByRole('button', { name: /сводка/i }).first().click();
    await page.waitForTimeout(500);
    await expect(page.getByText(/ожидают одобрения/i).first()).toBeVisible();
    await expect(page.locator('svg').first()).toBeVisible();
  });

  test('can approve created order in Kanban', async ({ page }) => {
    await page.getByRole('button', { name: /заявки/i }).first().click();
    await page.waitForTimeout(500);
    
    await page.getByRole('button', { name: /^одобрить$/i }).first().click();
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/одобрен/i);
  });

  test('can create new product', async ({ page }) => {
    await page.getByRole('button', { name: /товары/i }).first().click();
    await page.waitForTimeout(500);
    await page.getByTestId('add-product-button').click();
    
    await page.getByLabel(/наименование/i).fill('New Test Product');
    await page.getByLabel(/категория/i).first().fill('Молочная продукция');
    await page.getByLabel(/цена/i).fill('10.5');
    await page.getByLabel(/количество/i).fill('100');
    
    await page.getByRole('button', { name: /сохранить/i }).click();
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/товар/i);
  });

  test('can create new director user', async ({ page }) => {
    await page.getByRole('button', { name: /пользователи/i }).first().click();
    await page.waitForTimeout(500);
    
    await page.getByLabel(/логин/i).fill('new_director');
    await page.getByLabel(/пароль/i).fill('Pass123!');
    await page.getByLabel(/фио/i).fill('Full Name');
    await page.getByLabel(/юр. лицо/i).fill('Legal Entity');
    
    await page.getByTestId('create-user-button').click();
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/создан/i);
  });
});

test.describe('Logistician Flows', () => {
  test.beforeEach(async ({ page }) => {
    await installApiMock(page);
    await loginAs(page, 'logistician');
    await waitForWorkspaceReady(page);
  });

  test('can assign driver to approved order', async ({ page }) => {
    await expect(page.getByRole('heading').filter({ hasText: /логистика|назначения/i }).first()).toBeVisible();
    
    const driverSelect = page.getByTestId('driver-select-302').locator('.MuiSelect-select');
    await driverSelect.click();
    const option = page.getByRole('option', { name: /Driver One/i }).first();
    await expect(option).toBeVisible();
    await option.click();
    
    await page.getByRole('button', { name: /назначить/i }).first().click();
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/назначен/i);
  });

  test('can use auto-assign tool', async ({ page }) => {
    // Ensure orders are loaded and button is active
    await expect(page.getByTestId('auto-assign-button')).toBeEnabled({ timeout: 10_000 });
    await page.getByTestId('auto-assign-button').click();

    const driverSelectionTitle = page.getByRole('heading', { name: /выбор водителей/i });
    await expect(driverSelectionTitle).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: /построить план/i }).click();

    const dialogTitle = page.getByTestId('auto-assign-dialog-title');
    await expect(dialogTitle).toBeVisible({ timeout: 15_000 });
    
    await page.getByTestId('reject-route-plan-button').click();
    await expect
      .poll(
        async () => {
          const rejectAlert = page.locator('.MuiAlert-message').filter({ hasText: /отклон/i });
          if (await rejectAlert.count()) {
            return 'alert';
          }

          try {
            return (await dialogTitle.isVisible()) ? 'pending' : 'closed';
          } catch {
            return 'closed';
          }
        },
        { timeout: 10_000 }
      )
      .toMatch(/alert|closed/);
  });
});

test.describe('Driver Flows', () => {
  test.beforeEach(async ({ page }) => {
    await installApiMock(page);
    await loginAs(page, 'driver1');
    await waitForWorkspaceReady(page);
  });

  test('can mark assigned order as delivered', async ({ page }) => {
    await expect(page.getByRole('heading').filter({ hasText: /доставки/i }).first()).toBeVisible();
    await expect(page.getByText(/заказ #303/i).first()).toBeVisible();
    
    await page.getByRole('button', { name: /доставленным/i }).click();
    await expect(page.locator('.MuiAlert-message').first()).toContainText(/доставлен/i);
    
    await expect(page.getByText(/активных заказов:\s*0/i)).toBeVisible();
    await expect(page.getByText(/статус:\s*доставлен/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /уже доставлен/i })).toBeDisabled();
    await expect(page.getByRole('button', { name: /отметить доставленным/i })).toHaveCount(0);
  });
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
