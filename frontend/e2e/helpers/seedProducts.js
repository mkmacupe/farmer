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
  /createOrUpdateProduct\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*image\("([^"]+)"\)\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\);/gs;

export function loadDemoProductsFromDataInitializer() {
  const source = fs.readFileSync(DATA_INITIALIZER_PATH, 'utf-8');

  const allProducts = [];
  for (const match of source.matchAll(PRODUCT_REGEX)) {
    const [, name, category, description, imageFile, priceRaw, stockQuantityRaw] = match;
    allProducts.push({
      id: allProducts.length + 1,
      name,
      category,
      description,
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
