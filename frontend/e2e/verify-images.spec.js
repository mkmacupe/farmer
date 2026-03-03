import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/mockApi.js';
import { loadDemoProductsFromDataInitializer } from './helpers/seedProducts.js';

test('verify all seeded products have unique and valid images', async ({ page }) => {
  test.setTimeout(120_000);

  const products = loadDemoProductsFromDataInitializer();
  expect(products.length, 'Seeded demo products list should not be empty').toBeGreaterThan(0);

  await page.route('**/api/products*', async route => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: products,
        page: 0,
        size: Math.max(products.length, 24),
        totalItems: products.length,
        totalPages: 1,
        hasNext: false
      })
    });
  });

  await page.route('**/api/products/categories', async route => {
    await route.fulfill({ json: [...new Set(products.map(product => product.category))] });
  });

  await page.route('**/api/auth/login', async route => {
    await route.fulfill({
      json: { token: 'token-director', username: 'director', role: 'DIRECTOR' }
    });
  });

  await page.route('**/api/director/profile', async route => {
    await route.fulfill({ json: { fullName: 'Director Test' } });
  });

  await page.route('**/api/director/addresses', async route => {
    await route.fulfill({ json: [] });
  });

  await page.route('**/api/orders/my', async route => {
    await route.fulfill({ json: [] });
  });

  await page.route('**/api/notifications/stream', async route => {
    await route.fulfill({ status: 200, body: '' });
  });

  await loginAs(page, 'director');
  await page.getByRole('button', { name: /^каталог$/i }).click();

  const imageUrls = products.map(product => product.photoUrl);
  const uniqueImageUrls = new Set(imageUrls);
  expect(uniqueImageUrls.size).toBe(products.length);
  console.log(`Verified ${uniqueImageUrls.size} unique image URLs for ${products.length} seeded products.`);

  const catalogSection = page.locator('#director-catalog');
  await expect(catalogSection).toBeVisible();

  for (const product of products) {
    console.log(`Verifying image for: ${product.name}`);

    const image = catalogSection.getByRole('img', { name: product.name }).first();
    await expect(image).toHaveCount(1);
    await expect(image).toBeVisible();
    await expect(image).toHaveAttribute('src', product.photoUrl);

    const response = await page.request.get(product.photoUrl);
    expect(
      response.status(),
      `Image ${product.photoUrl} for ${product.name} should exist (status: ${response.status()})`
    ).toBe(200);

    const contentType = response.headers()['content-type'] || '';
    expect(contentType).toMatch(/^image\//);
  }

  console.log(`SUCCESS: All ${products.length} seeded product images are unique and load with status 200.`);
});
