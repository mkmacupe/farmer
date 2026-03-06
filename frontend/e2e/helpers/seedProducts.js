import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DATA_INITIALIZER_PATH = path.resolve(
  __dirname,
  '..',
  '..',
  '..',
  'backend',
  'src',
  'main',
  'java',
  'com',
  'farm',
  'sales',
  'config',
  'DataInitializer.java'
);

const PRODUCT_REGEX =
  /seedProduct\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"([^"]+)"\s*\);/gs;

export function loadDemoProductsFromDataInitializer() {
  const source = fs.readFileSync(DATA_INITIALIZER_PATH, 'utf-8');

  const allProducts = [];
  for (const match of source.matchAll(PRODUCT_REGEX)) {
    const [, name, category, priceRaw, stockQuantityRaw, imageFile] = match;
    allProducts.push({
      id: allProducts.length + 1,
      name,
      category,
      description: name, // In DataInitializer, name is used for description too in new Product()
      photoUrl: `/images/products/${imageFile}`,
      price: Number.parseFloat(priceRaw),
      stockQuantity: Number.parseInt(stockQuantityRaw, 10)
    });
  }

  if (allProducts.length === 0) {
    throw new Error(`Could not parse products from ${DATA_INITIALIZER_PATH}`);
  }

  return allProducts;
}
