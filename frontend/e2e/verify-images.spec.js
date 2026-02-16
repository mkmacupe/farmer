import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/mockApi.js';

test('verify all products have unique and valid images', async ({ page }) => {
  // 1. Define the full list of products (mirrors backend DataInitializer.java)
  const products = [
    { id: 1, name: "Молоко 1 л", category: "Молочная продукция", description: "Свежее коровье молоко с фермы", photoUrl: "/images/products/milk.webp", price: 2.98, stockQuantity: 120 },
    { id: 2, name: "Сыр 0.5 кг", category: "Молочная продукция", description: "Фермерский сыр ручной работы", photoUrl: "/images/products/cheese.webp", price: 13.42, stockQuantity: 60 },
    { id: 3, name: "Мёд 0.5 кг", category: "Мёд", description: "Натуральный акациевый мёд", photoUrl: "/images/products/honey.webp", price: 27.43, stockQuantity: 40 },
    { id: 4, name: "Томаты 1 кг", category: "Овощи", description: "Сезонные тепличные томаты", photoUrl: "/images/products/tomato.webp", price: 11.19, stockQuantity: 85 },
    { id: 5, name: "Кефир 1 л", category: "Молочная продукция", description: "Кефир 2,5% жирности", photoUrl: "/images/products/kefir.webp", price: 3.06, stockQuantity: 90 },
    { id: 6, name: "Йогурт натуральный 0.5 л", category: "Молочная продукция", description: "Натуральный йогурт без сахара", photoUrl: "/images/products/yogurt.webp", price: 5.56, stockQuantity: 70 },
    { id: 7, name: "Творог 0.5 кг", category: "Молочная продукция", description: "Домашний творог", photoUrl: "/images/products/cottage-cheese.webp", price: 22.31, stockQuantity: 55 },
    { id: 8, name: "Сметана 0.4 л", category: "Молочная продукция", description: "Сметана 20% жирности", photoUrl: "/images/products/sour-cream.webp", price: 4.13, stockQuantity: 60 },
    { id: 9, name: "Сливочное масло 0.2 кг", category: "Молочная продукция", description: "Масло 82,5%", photoUrl: "/images/products/butter.webp", price: 6.15, stockQuantity: 50 },
    { id: 10, name: "Яйца 10 шт", category: "Птица", description: "Домашние яйца", photoUrl: "/images/products/egg.webp", price: 3.45, stockQuantity: 200 },
    { id: 11, name: "Курица охлаждённая 1 кг", category: "Мясо", description: "Фермерская курица", photoUrl: "/images/products/chicken.webp", price: 7.59, stockQuantity: 45 },
    { id: 12, name: "Говядина 1 кг", category: "Мясо", description: "Мякоть для тушения", photoUrl: "/images/products/beef.webp", price: 27.88, stockQuantity: 30 },
    { id: 13, name: "Свинина 1 кг", category: "Мясо", description: "Фермерская свинина", photoUrl: "/images/products/pork.webp", price: 15.99, stockQuantity: 40 },
    { id: 14, name: "Картофель 5 кг", category: "Овощи", description: "Отборный картофель", photoUrl: "/images/products/potato.webp", price: 12.15, stockQuantity: 120 },
    { id: 15, name: "Морковь 2 кг", category: "Овощи", description: "Свежая морковь", photoUrl: "/images/products/carrot.webp", price: 2.20, stockQuantity: 110 },
    { id: 16, name: "Лук репчатый 2 кг", category: "Овощи", description: "Жёлтый лук", photoUrl: "/images/products/onion.webp", price: 3.56, stockQuantity: 100 },
    { id: 17, name: "Огурцы 1 кг", category: "Овощи", description: "Хрустящие огурцы", photoUrl: "/images/products/cucumber.webp", price: 11.19, stockQuantity: 80 },
    { id: 18, name: "Яблоки 1 кг", category: "Фрукты", description: "Сезонные яблоки", photoUrl: "/images/products/apple.webp", price: 4.55, stockQuantity: 95 },
    { id: 19, name: "Груши 1 кг", category: "Фрукты", description: "Сладкие груши", photoUrl: "/images/products/pear.webp", price: 7.62, stockQuantity: 70 },
    { id: 20, name: "Клубника 0.5 кг", category: "Фрукты", description: "Свежая клубника", photoUrl: "/images/products/strawberry.webp", price: 5.15, stockQuantity: 40 },
    { id: 21, name: "Мёд липовый 0.5 кг", category: "Мёд", description: "Липовый мёд", photoUrl: "/images/products/honey-linden.webp", price: 27.43, stockQuantity: 35 },
    { id: 22, name: "Хлеб ржаной 0.6 кг", category: "Хлеб", description: "Ржаной хлеб", photoUrl: "/images/products/rye-bread.webp", price: 2.08, stockQuantity: 90 },
    { id: 23, name: "Батон 0.4 кг", category: "Хлеб", description: "Пшеничный батон", photoUrl: "/images/products/baguette.webp", price: 1.45, stockQuantity: 100 },
    { id: 24, name: "Крупа гречневая 1 кг", category: "Крупы", description: "Гречка ядрица", photoUrl: "/images/products/buckwheat.webp", price: 2.59, stockQuantity: 85 },
    { id: 25, name: "Рис 1 кг", category: "Крупы", description: "Рис шлифованный", photoUrl: "/images/products/rice.webp", price: 2.59, stockQuantity: 90 },
    { id: 26, name: "Пшено 1 кг", category: "Крупы", description: "Пшено шлифованное", photoUrl: "/images/products/millet.webp", price: 2.59, stockQuantity: 75 },
    { id: 27, name: "Сок яблочный 1 л", category: "Напитки", description: "Натуральный сок из фермерских яблок", photoUrl: "/images/products/apple-juice.webp", price: 2.98, stockQuantity: 80 },
    { id: 28, name: "Вода питьевая артезианская 1.5 л", category: "Напитки", description: "Питьевая вода из артезианской скважины фермы", photoUrl: "/images/products/water.webp", price: 4.37, stockQuantity: 150 },
    { id: 29, name: "Молоко 2 л", category: "Молочная продукция", description: "Молоко пастеризованное 3,2%", photoUrl: "/images/products/milk-2l.webp", price: 5.76, stockQuantity: 90 },
    { id: 30, name: "Кефир 0.5 л", category: "Молочная продукция", description: "Кефир 2,5% жирности", photoUrl: "/images/products/kefir-05l.webp", price: 1.63, stockQuantity: 140 },
    { id: 31, name: "Йогурт с фруктами 0.5 л", category: "Молочная продукция", description: "Йогурт с фруктовыми кусочками", photoUrl: "/images/products/yogurt-fruit.webp", price: 5.15, stockQuantity: 110 },
    { id: 32, name: "Мёд гречишный 0.5 кг", category: "Мёд", description: "Ароматный гречишный мёд", photoUrl: "/images/products/honey-buckwheat.webp", price: 27.43, stockQuantity: 25 },
    { id: 33, name: "Сыр твёрдый 1 кг", category: "Молочная продукция", description: "Выдержанный твёрдый сыр", photoUrl: "/images/products/cheese-hard.webp", price: 26.64, stockQuantity: 35 },
    { id: 34, name: "Томаты черри 0.5 кг", category: "Овощи", description: "Сладкие томаты черри", photoUrl: "/images/products/tomato-cherry.webp", price: 9.55, stockQuantity: 70 },
  ];

  // 2. Mock API to return this full list
  await page.route('**/api/products*', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: products, page: 0, size: 100, totalItems: products.length, hasNext: false }) // DirectorView uses getProductsPage structure
      });
    } else {
      await route.continue();
    }
  });

  await page.route('**/api/products/categories', async route => {
     await route.fulfill({ json: [...new Set(products.map(p => p.category))] });
  });

  // Handle other mock routes needed for login and basic view
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
     await route.fulfill({ status: 200, body: '' }); // Keep connection open or just close
  });

  // 3. Login and go to Catalog
  await loginAs(page, 'director');
  await page.getByRole('button', { name: /^каталог$/i }).click();

  // 4. Verify uniqueness of URLs in our data (static check)
  const urls = products.map(p => p.photoUrl);
  const uniqueUrls = new Set(urls);
  expect(uniqueUrls.size).toBe(products.length);
  console.log(`Verified ${uniqueUrls.size} unique image URLs for ${products.length} products.`);

  // 5. Verify images load on the page
  // We wait for the products to be rendered
  // Use .first() because 'Milk' might appear in 'New Arrivals' and 'Catalog'
  await expect(page.getByText('Молоко 1 л').first()).toBeVisible();

  // Scroll to trigger lazy loading if needed, or just force load
  // The Catalog has pagination or load more. We mocked size=100 so all should be there.
  
  const catalogSection = page.locator('#director-catalog');
  await expect(catalogSection).toBeVisible();

  // Iterate and check image status
  for (const product of products) {
    console.log(`Verifying image for: ${product.name}`);
    // Find the image element for this product within the catalog
    // We assume the alt text is the product name (DirectorView: alt={product.name})
    // We use .first() because the product might be in the "Featured" section too.
    // Ideally we'd scope to #director-catalog, but ProductImage might not be direct child.
    
    // Scoping to catalog to be safe
    const img = catalogSection.getByRole('img', { name: product.name }).first();
    
    // Scroll into view to ensure lazy loaded images trigger request (if any)
    // although we are checking src attribute which should be present in React Virtual DOM
    await img.scrollIntoViewIfNeeded(); 
    
    await expect(img).toBeVisible();
    await expect(img).toHaveAttribute('src', product.photoUrl);

    // Verify the image resource actually exists (server returns 200)
    // We can do this by requesting it directly from the page context
    const response = await page.request.get(product.photoUrl);
    expect(response.status(), `Image ${product.photoUrl} for ${product.name} should exist (Status: ${response.status()})`).toBe(200);
    
    // Optional: Check content type
    expect(response.headers()['content-type']).toMatch(/^image\//);
  }
  
  console.log("SUCCESS: All 34 product images are unique, assigned correctly, and load with status 200.");
});
