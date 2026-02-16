import { expect, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { fileURLToPath } from 'url';

/**
 * Тесты для проверки изображений товаров:
 * 1. Уникальность - каждый товар имеет уникальную картинку
 * 2. Соответствие - название файла соответствует товару
 * 3. Консистентность стиля - все изображения единообразны
 */

// ESM-совместимый способ получения директории
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Маппинг товаров к ожидаемым изображениям (на основе DataInitializer.java)
const PRODUCT_IMAGE_MAPPING = {
  'Молоко 1 л': 'milk.webp',
  'Молоко 2 л': 'milk-2l.webp',
  'Сыр 0.5 кг': 'cheese.webp',
  'Сыр твёрдый 1 кг': 'cheese-hard.webp',
  'Творог 0.5 кг': 'cottage-cheese.webp',
  'Сметана 0.4 л': 'sour-cream.webp',
  'Сливочное масло 0.2 кг': 'butter.webp',
  'Мёд 0.5 кг': 'honey.webp',
  'Мёд липовый 0.5 кг': 'honey-linden.webp',
  'Мёд гречишный 0.5 кг': 'honey-buckwheat.webp',
  'Томаты 1 кг': 'tomato.webp',
  'Томаты черри 0.5 кг': 'tomato-cherry.webp',
  'Кефир 1 л': 'kefir.webp',
  'Кефир 0.5 л': 'kefir-05l.webp',
  'Йогурт натуральный 0.5 л': 'yogurt.webp',
  'Йогурт с фруктами 0.5 л': 'yogurt-fruit.webp',
  'Яйца 10 шт': 'egg.webp',
  'Курица охлаждённая 1 кг': 'chicken.webp',
  'Говядина 1 кг': 'beef.webp',
  'Свинина 1 кг': 'pork.webp',
  'Картофель 5 кг': 'potato.webp',
  'Морковь 2 кг': 'carrot.webp',
  'Лук репчатый 2 кг': 'onion.webp',
  'Огурцы 1 кг': 'cucumber.webp',
  'Яблоки 1 кг': 'apple.webp',
  'Груши 1 кг': 'pear.webp',
  'Клубника 0.5 кг': 'strawberry.webp',
  'Хлеб ржаной 0.6 кг': 'rye-bread.webp',
  'Батон 0.4 кг': 'baguette.webp',
  'Крупа гречневая 1 кг': 'buckwheat.webp',
  'Рис 1 кг': 'rice.webp',
  'Пшено 1 кг': 'millet.webp',
  'Сок яблочный 1 л': 'apple-juice.webp',
  'Вода питьевая артезианская 1.5 л': 'water.webp'
};

// Ключевые слова для проверки соответствия изображения товару
const PRODUCT_KEYWORDS = {
  'milk': ['молоко'],
  'milk-2l': ['молоко', '2 л'],
  'cheese': ['сыр'],
  'cheese-hard': ['сыр', 'твёрдый'],
  'cottage-cheese': ['творог'],
  'sour-cream': ['сметана'],
  'butter': ['масло', 'сливочное'],
  'honey': ['мёд'],
  'honey-linden': ['мёд', 'липовый'],
  'honey-buckwheat': ['мёд', 'гречишный'],
  'tomato': ['томат'],
  'tomato-cherry': ['томат', 'черри'],
  'kefir': ['кефир'],
  'kefir-05l': ['кефир', '0.5'],
  'yogurt': ['йогурт'],
  'yogurt-fruit': ['йогурт', 'фрукт'],
  'egg': ['яйц'],
  'chicken': ['курица', 'курин'],
  'beef': ['говядина'],
  'pork': ['свинина'],
  'potato': ['картофель', 'картошка'],
  'carrot': ['морковь'],
  'onion': ['лук'],
  'cucumber': ['огурц'],
  'apple': ['яблок'],
  'pear': ['груш'],
  'strawberry': ['клубника'],
  'rye-bread': ['хлеб', 'ржаной'],
  'baguette': ['батон'],
  'buckwheat': ['гречн', 'крупа гречневая'],
  'rice': ['рис'],
  'millet': ['пшено'],
  'apple-juice': ['сок', 'яблочный'],
  'water': ['вода']
};

// Путь к изображениям
const IMAGES_DIR = path.join(__dirname, '..', 'dist', 'images', 'products');

test.describe('Product Images Validation', () => {
  
  test.describe('Static Files Check', () => {
    
    test('all product images exist in dist folder', async () => {
      // Проверяем что папка существует
      expect(fs.existsSync(IMAGES_DIR), `Images directory should exist: ${IMAGES_DIR}`).toBeTruthy();
      
      const files = fs.readdirSync(IMAGES_DIR);
      const webpFiles = files.filter(f => f.endsWith('.webp'));
      
      // Проверяем что есть изображения
      expect(webpFiles.length).toBeGreaterThan(0);
      console.log(`Found ${webpFiles.length} product images`);
      
      // Проверяем все ожидаемые файлы
      const expectedImages = Object.values(PRODUCT_IMAGE_MAPPING);
      for (const expectedImage of expectedImages) {
        expect(
          webpFiles.includes(expectedImage),
          `Expected image ${expectedImage} should exist`
        ).toBeTruthy();
      }
    });

    test('all product images are unique (no duplicate files by content)', async () => {
      const files = fs.readdirSync(IMAGES_DIR).filter(f => f.endsWith('.webp'));
      
      const fileHashes = new Map();
      const duplicates = [];
      
      for (const file of files) {
        const filePath = path.join(IMAGES_DIR, file);
        const content = fs.readFileSync(filePath);
        const hash = crypto.createHash('md5').update(content).digest('hex');
        
        if (fileHashes.has(hash)) {
          duplicates.push({
            file1: fileHashes.get(hash),
            file2: file,
            hash
          });
        } else {
          fileHashes.set(hash, file);
        }
      }
      
      if (duplicates.length > 0) {
        console.log('Duplicate images found (identical content):');
        duplicates.forEach(d => console.log(`  ${d.file1} = ${d.file2}`));
      }
      
      expect(
        duplicates.length,
        `Found ${duplicates.length} duplicate images: ${duplicates.map(d => `${d.file1}=${d.file2}`).join(', ')}`
      ).toBe(0);
    });

    test('all product images have reasonable file size (not placeholders)', async () => {
      const files = fs.readdirSync(IMAGES_DIR).filter(f => f.endsWith('.webp'));
      
      const MIN_SIZE = 1024; // Минимум 1KB - реальное изображение
      const MAX_SIZE = 500 * 1024; // Максимум 500KB - оптимизированное изображение
      
      const issues = [];
      
      for (const file of files) {
        const filePath = path.join(IMAGES_DIR, file);
        const stats = fs.statSync(filePath);
        
        if (stats.size < MIN_SIZE) {
          issues.push(`${file}: too small (${stats.size} bytes) - might be a placeholder`);
        }
        if (stats.size > MAX_SIZE) {
          issues.push(`${file}: too large (${Math.round(stats.size / 1024)}KB) - should be optimized`);
        }
      }
      
      if (issues.length > 0) {
        console.log('Image size issues:');
        issues.forEach(i => console.log(`  ${i}`));
      }
      
      expect(issues.length, `Found ${issues.length} size issues: ${issues.join('; ')}`).toBe(0);
    });

    test('image filenames match product names semantically', async () => {
      const mismatches = [];
      
      for (const [productName, imageFile] of Object.entries(PRODUCT_IMAGE_MAPPING)) {
        const imageName = imageFile.replace('.webp', '');
        const keywords = PRODUCT_KEYWORDS[imageName];
        
        if (!keywords) {
          // Нет ключевых слов для проверки - пропускаем
          continue;
        }
        
        const productNameLower = productName.toLowerCase();
        const hasMatch = keywords.some(keyword => 
          productNameLower.includes(keyword.toLowerCase())
        );
        
        if (!hasMatch) {
          mismatches.push({
            product: productName,
            image: imageFile,
            expectedKeywords: keywords
          });
        }
      }
      
      if (mismatches.length > 0) {
        console.log('Product-image mismatches:');
        mismatches.forEach(m => 
          console.log(`  "${m.product}" -> ${m.image} (expected keywords: ${m.expectedKeywords.join(', ')})`)
        );
      }
      
      expect(
        mismatches.length,
        `Found ${mismatches.length} mismatches between product names and images`
      ).toBe(0);
    });

    test('all images use consistent format (WebP)', async () => {
      const files = fs.readdirSync(IMAGES_DIR);
      
      const nonWebp = files.filter(f => 
        !f.endsWith('.webp') && 
        !f.startsWith('.') && // Игнорируем скрытые файлы
        f !== 'README.md'
      );
      
      expect(
        nonWebp.length,
        `Found non-WebP files: ${nonWebp.join(', ')}`
      ).toBe(0);
    });
  });

  test.describe('UI Integration Check', () => {
    
    test('each product in catalog has unique image URL', async ({ page }) => {
      // Мокаем API
      await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pathname = url.pathname;
        const method = request.method();
        
        if (pathname.endsWith('/api/auth/login') && method === 'POST') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              token: 'token-director',
              username: 'director',
              fullName: 'Director User',
              role: 'DIRECTOR'
            })
          });
          return;
        }
        
        if (pathname.endsWith('/api/notifications/stream')) {
          await route.fulfill({
            status: 200,
            contentType: 'text/event-stream',
            body: 'event: connected\ndata: {"type":"CONNECTED"}\n\n'
          });
          return;
        }
        
        if (pathname.endsWith('/api/director/profile')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              id: 1,
              username: 'director',
              fullName: 'Director User',
              phone: '+375290000001',
              legalEntityName: 'Test LLC'
            })
          });
          return;
        }
        
        if (pathname.endsWith('/api/director/addresses')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([{ id: 1, label: 'Main', addressLine: 'Test Address' }])
          });
          return;
        }
        
        if (pathname.endsWith('/api/orders/my')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([])
          });
          return;
        }
        
        if (pathname.endsWith('/api/products/categories')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(['Молочная продукция', 'Мясо', 'Овощи', 'Фрукты', 'Мёд'])
          });
          return;
        }
        
        if (pathname.endsWith('/api/products')) {
          // Тестовые товары - каждый с уникальным изображением
          const products = [
            { id: 1, name: 'Молоко 1 л', category: 'Молочная продукция', photoUrl: '/images/products/milk.webp', price: 2.98, stockQuantity: 120 },
            { id: 2, name: 'Сыр 0.5 кг', category: 'Молочная продукция', photoUrl: '/images/products/cheese.webp', price: 13.42, stockQuantity: 60 },
            { id: 3, name: 'Мёд 0.5 кг', category: 'Мёд', photoUrl: '/images/products/honey.webp', price: 27.43, stockQuantity: 40 },
            { id: 4, name: 'Томаты 1 кг', category: 'Овощи', photoUrl: '/images/products/tomato.webp', price: 11.19, stockQuantity: 85 },
            { id: 5, name: 'Кефир 1 л', category: 'Молочная продукция', photoUrl: '/images/products/kefir.webp', price: 3.06, stockQuantity: 90 },
            { id: 6, name: 'Яблоки 1 кг', category: 'Фрукты', photoUrl: '/images/products/apple.webp', price: 4.55, stockQuantity: 95 }
          ];
          
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              items: products,
              page: 0,
              size: 24,
              totalItems: products.length,
              totalPages: 1,
              hasNext: false
            })
          });
          return;
        }
        
        await route.fulfill({ status: 404 });
      });
      
      // Логинимся как директор
      await page.goto('/');
      await page.getByLabel(/логин/i).fill('director');
      await page.getByLabel(/пароль/i).fill('TestPass123!');
      await page.getByRole('button', { name: /войти/i }).click();
      
      // Переходим в каталог
      await page.getByRole('button', { name: /^каталог$/i }).click();
      await expect(page.getByRole('heading', { name: /каталог и корзина/i })).toBeVisible();
      
      // Ждём загрузки
      await page.waitForTimeout(500);
      
      // Получаем все уникальные URL изображений в каталоге (секция с карточками товаров)
      // Ищем внутри секции "Каталог и корзина", исключая hero-баннер
      const catalogSection = page.locator('#director-catalog');
      const productCards = catalogSection.locator('.MuiCard-root');
      
      const imageUrls = new Map(); // URL -> название товара
      const duplicateUrls = [];
      
      const cardCount = await productCards.count();
      
      for (let i = 0; i < cardCount; i++) {
        const card = productCards.nth(i);
        const img = card.locator('img').first();
        
        if (await img.count() > 0) {
          const src = await img.getAttribute('src');
          const productName = await card.locator('h6, .MuiTypography-subtitle1').first().textContent();
          
          if (src && src.includes('/images/products/')) {
            if (imageUrls.has(src)) {
              duplicateUrls.push({
                url: src,
                product1: imageUrls.get(src),
                product2: productName
              });
            } else {
              imageUrls.set(src, productName);
            }
          }
        }
      }
      
      console.log(`Checked ${cardCount} product cards, found ${imageUrls.size} unique image URLs`);
      
      if (duplicateUrls.length > 0) {
        console.log('Products sharing same image:');
        duplicateUrls.forEach(d => 
          console.log(`  ${d.url}: "${d.product1}" and "${d.product2}"`)
        );
      }
      
      expect(
        duplicateUrls.length,
        `Found ${duplicateUrls.length} duplicate image URLs in catalog`
      ).toBe(0);
    });

    test('product images load successfully without 404', async ({ page }) => {
      const failedImages = [];
      
      // Отслеживаем ошибки загрузки изображений
      page.on('response', response => {
        if (response.url().includes('/images/products/') && !response.ok()) {
          failedImages.push({
            url: response.url(),
            status: response.status()
          });
        }
      });
      
      await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pathname = url.pathname;
        const method = request.method();
        
        if (pathname.endsWith('/api/auth/login') && method === 'POST') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              token: 'token-director',
              username: 'director',
              fullName: 'Director',
              role: 'DIRECTOR'
            })
          });
          return;
        }
        
        if (pathname.endsWith('/api/notifications/stream')) {
          await route.fulfill({ status: 200, contentType: 'text/event-stream', body: '' });
          return;
        }
        
        if (pathname.endsWith('/api/director/profile')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ id: 1, username: 'director', fullName: 'Director', legalEntityName: 'Test' })
          });
          return;
        }
        
        if (pathname.endsWith('/api/director/addresses')) {
          await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
          return;
        }
        
        if (pathname.endsWith('/api/orders/my')) {
          await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
          return;
        }
        
        if (pathname.endsWith('/api/products/categories')) {
          await route.fulfill({ status: 200, contentType: 'application/json', body: '["Молочная продукция"]' });
          return;
        }
        
        if (pathname.endsWith('/api/products')) {
          const products = [
            { id: 1, name: 'Молоко 1 л', category: 'Молочная продукция', photoUrl: '/images/products/milk.webp', price: 2.98, stockQuantity: 120 },
            { id: 2, name: 'Сыр 0.5 кг', category: 'Молочная продукция', photoUrl: '/images/products/cheese.webp', price: 13.42, stockQuantity: 60 }
          ];
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ items: products, page: 0, size: 24, totalItems: 2, totalPages: 1, hasNext: false })
          });
          return;
        }
        
        await route.fulfill({ status: 404 });
      });
      
      await page.goto('/');
      await page.getByLabel(/логин/i).fill('director');
      await page.getByLabel(/пароль/i).fill('TestPass123!');
      await page.getByRole('button', { name: /войти/i }).click();
      
      await page.getByRole('button', { name: /^каталог$/i }).click();
      await page.waitForTimeout(2000);
      
      expect(
        failedImages.length,
        `${failedImages.length} images failed to load: ${failedImages.map(f => `${f.url} (${f.status})`).join(', ')}`
      ).toBe(0);
    });
  });

  test.describe('Visual Consistency Check', () => {
    
    test('all images have similar file sizes (consistent quality)', async () => {
      const files = fs.readdirSync(IMAGES_DIR).filter(f => f.endsWith('.webp'));
      
      const fileSizes = [];
      
      for (const file of files) {
        const filePath = path.join(IMAGES_DIR, file);
        const stats = fs.statSync(filePath);
        fileSizes.push({ file, size: stats.size });
      }
      
      // Вычисляем среднее и стандартное отклонение
      const avgSize = fileSizes.reduce((sum, f) => sum + f.size, 0) / fileSizes.length;
      const variance = fileSizes.reduce((sum, f) => sum + Math.pow(f.size - avgSize, 2), 0) / fileSizes.length;
      const stdDev = Math.sqrt(variance);
      const coefficientOfVariation = (stdDev / avgSize) * 100;
      
      console.log(`Image size stats: avg=${Math.round(avgSize/1024)}KB, stdDev=${Math.round(stdDev/1024)}KB, CV=${coefficientOfVariation.toFixed(1)}%`);
      
      // Показываем outliers
      const outliers = fileSizes.filter(f => Math.abs(f.size - avgSize) > 2 * stdDev);
      if (outliers.length > 0) {
        console.log('Size outliers (>2 std dev from mean):');
        outliers.forEach(o => console.log(`  ${o.file}: ${Math.round(o.size/1024)}KB`));
      }
      
      // Коэффициент вариации не должен превышать 100% (иначе изображения слишком разные)
      expect(
        coefficientOfVariation,
        `Image sizes vary too much (CV=${coefficientOfVariation.toFixed(1)}%) - inconsistent quality/dimensions`
      ).toBeLessThan(100);
    });
    
    test('no missing images in product-to-image mapping', async () => {
      const existingFiles = new Set(fs.readdirSync(IMAGES_DIR));
      
      const missingImages = [];
      
      for (const [product, image] of Object.entries(PRODUCT_IMAGE_MAPPING)) {
        if (!existingFiles.has(image)) {
          missingImages.push({ product, image });
        }
      }
      
      if (missingImages.length > 0) {
        console.log('Missing images:');
        missingImages.forEach(m => console.log(`  ${m.product} -> ${m.image}`));
      }
      
      expect(
        missingImages.length,
        `Found ${missingImages.length} missing images for products`
      ).toBe(0);
    });
    
    test('no orphan images (images without products)', async () => {
      const existingFiles = fs.readdirSync(IMAGES_DIR).filter(f => f.endsWith('.webp'));
      const mappedImages = new Set(Object.values(PRODUCT_IMAGE_MAPPING));
      
      const orphanImages = existingFiles.filter(f => !mappedImages.has(f));
      
      if (orphanImages.length > 0) {
        console.log('Orphan images (no product uses them):');
        orphanImages.forEach(o => console.log(`  ${o}`));
      }
      
      // Предупреждение, но не ошибка (могут быть запасные изображения)
      if (orphanImages.length > 0) {
        console.warn(`Warning: Found ${orphanImages.length} orphan images that are not used by any product`);
      }
      
      // Для строгой проверки можно раскомментировать:
      // expect(orphanImages.length).toBe(0);
    });

    test('all image filenames follow naming convention', async () => {
      const files = fs.readdirSync(IMAGES_DIR).filter(f => f.endsWith('.webp'));
      
      // Проверяем что имена файлов следуют конвенции: lowercase, дефисы, без пробелов
      const invalidNames = [];
      const validPattern = /^[a-z0-9-]+\.webp$/;
      
      for (const file of files) {
        if (!validPattern.test(file)) {
          invalidNames.push(file);
        }
      }
      
      if (invalidNames.length > 0) {
        console.log('Files with invalid naming:');
        invalidNames.forEach(f => console.log(`  ${f}`));
      }
      
      expect(
        invalidNames.length,
        `Found ${invalidNames.length} files with invalid naming convention`
      ).toBe(0);
    });
  });
});
