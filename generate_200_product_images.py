#!/usr/bin/env python3
"""
Generate a full 200-image Farm Sales product catalog through the xAI Imagine API.

What this script does:
1. Reconstructs the canonical 200 seeded products directly from the repo sources.
2. Builds one consistent prompt template for every product.
3. Generates missing images in parallel via the xAI OpenAI-compatible API.
4. Saves files as product_images/product_0001.jpg ... product_0200.jpg
5. Writes a manifest CSV for traceability.

The script is intentionally self-contained and repo-aware, so it stays aligned
with the current Farm Sales catalog instead of relying on a stale hardcoded list.
"""

from __future__ import annotations

import argparse
import base64
import getpass
import os
import re
import threading
import time
import csv
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

try:
    import pandas as pd
except ImportError:  # pragma: no cover - optional convenience dependency
    pd = None
try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - optional convenience dependency
    def load_dotenv(*_args, **_kwargs) -> bool:
        return False


PROJECT_ROOT = Path(__file__).resolve().parent
DATA_INITIALIZER = PROJECT_ROOT / "backend" / "src" / "main" / "java" / "com" / "farm" / "sales" / "config" / "DataInitializer.java"
PRODUCT_IMAGES_SOURCE = PROJECT_ROOT / "frontend" / "public" / "images" / "products"
OUTPUT_DIR = PROJECT_ROOT / "product_images"
MANIFEST_CSV = OUTPUT_DIR / "products_manifest.csv"

XAI_BASE_URL = "https://api.x.ai/v1"
DEFAULT_MODEL = "grok-imagine-image"
DEFAULT_WORKERS = 14
MAX_RETRIES = 2
PRICE_PER_IMAGE_USD = 0.02

# The frontend reuses the same product asset in two contexts:
# - primary catalog cards: responsive width + fixed 160px height
# - manager thumbnails: 56x56 / 72x72 square thumbs
# There is no single hard fixed ratio in code, but the dominant catalog slot is
# a landscape frame. 3:2 is the best compromise for the main card layout while
# still working acceptably in the square thumbnail with object-fit: contain.
ASPECT_RATIO = "3:2"

# xAI docs currently expose 1k and 2k output resolutions for image generation.
# This script is configured for 1k output to match the current requirement.
RESOLUTION = "1k"

PROMPT_TEMPLATE = (
    "Professional studio product photography of {product_name}. "
    "Pure white seamless background, centered perfect composition, single product only, "
    "the product fills the frame cleanly and consistently, consistent camera angle, "
    "soft natural studio lighting, commercial e-commerce quality, highly detailed, "
    "realistic textures, appetizing look, ultra-clean catalog shot, high-end commercial quality, "
    f"aspect ratio {ASPECT_RATIO}. "
    "Strictly no text, captions, labels, stickers, logos, branding, packaging design text, "
    "people, hands, cutlery, props, watermarks, borders, collage, or background clutter."
)

_THREAD_LOCAL = threading.local()


@dataclass(frozen=True, slots=True)
class Product:
    id: int
    name: str
    source_image_name: str

    @property
    def output_filename(self) -> str:
        return f"product_{self.id:04d}.jpg"

    @property
    def output_path(self) -> Path:
        return OUTPUT_DIR / self.output_filename


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate 200 Farm Sales product images with the xAI Imagine API."
    )
    parser.add_argument("--workers", type=int, default=DEFAULT_WORKERS, help="Parallel worker count (default: 14)")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="xAI image model (default: grok-imagine-image)")
    parser.add_argument("--limit", type=int, default=None, help="Optional debug limit for the number of products")
    return parser.parse_args()


def load_api_key() -> str:
    """Read XAI_API_KEY from .env/environment, otherwise ask interactively."""
    load_dotenv(PROJECT_ROOT / ".env")
    api_key = os.getenv("XAI_API_KEY", "").strip()
    if api_key:
        return api_key

    entered = getpass.getpass("Enter xAI API key (XAI_API_KEY / sk-...): ").strip()
    if not entered:
        raise SystemExit("xAI API key is required.")
    return entered


def get_client(api_key: str):
    """Create one OpenAI client per worker thread."""
    client = getattr(_THREAD_LOCAL, "client", None)
    if client is None:
        try:
            from openai import OpenAI
        except ImportError as exc:  # pragma: no cover - runtime dependency
            raise RuntimeError(
                "The 'openai' package is required. Install dependencies first: "
                "pip install openai pandas tqdm requests python-dotenv"
            ) from exc
        client = OpenAI(base_url=XAI_BASE_URL, api_key=api_key)
        _THREAD_LOCAL.client = client
    return client


def extract_products() -> list[Product]:
    """
    Reproduce the same canonical catalog order used by DataInitializer:
    - first 20 products are explicit seedProduct(...) calls
    - remaining 180 products are the first supplemental image basenames,
      sorted alphabetically and resolved through CatalogDescriptor entries
    """
    if not DATA_INITIALIZER.exists():
        raise FileNotFoundError(f"Missing DataInitializer.java: {DATA_INITIALIZER}")
    if not PRODUCT_IMAGES_SOURCE.exists():
        raise FileNotFoundError(f"Missing product image directory: {PRODUCT_IMAGES_SOURCE}")

    source = DATA_INITIALIZER.read_text(encoding="utf-8")

    core_products = _extract_core_products(source)
    descriptor_map = _extract_catalog_descriptors(source)

    core_image_names = {product.source_image_name for product in core_products}
    supplemental_image_names = sorted(
        path.name
        for path in PRODUCT_IMAGES_SOURCE.iterdir()
        if path.is_file() and path.suffix.lower() == ".webp" and path.name not in core_image_names
    )

    remaining = 200 - len(core_products)
    if remaining <= 0:
        raise RuntimeError("Core product list unexpectedly exceeds 200 items.")
    if len(supplemental_image_names) < remaining:
        raise RuntimeError(
            f"Expected at least {remaining} supplemental images, found {len(supplemental_image_names)}."
        )

    supplemental_products: list[Product] = []
    for offset, image_name in enumerate(supplemental_image_names[:remaining], start=len(core_products) + 1):
        basename = image_name.removesuffix(".webp")
        descriptor = descriptor_map.get(basename)
        if descriptor is None:
            raise RuntimeError(f"Missing CatalogDescriptor for supplemental image '{image_name}'.")
        supplemental_products.append(Product(id=offset, name=descriptor["name"], source_image_name=image_name))

    products = core_products + supplemental_products
    if len(products) != 200:
        raise RuntimeError(f"Expected exactly 200 products, reconstructed {len(products)}.")

    return products


def _extract_core_products(source: str) -> list[Product]:
    seed_calls = re.findall(
        r'seedProduct\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*\d+,\s*"([^"]+)"\);',
        source,
    )
    if len(seed_calls) < 20:
        raise RuntimeError(f"Expected at least 20 seedProduct calls, found {len(seed_calls)}.")

    core = [
        Product(id=index, name=name, source_image_name=image_name)
        for index, (name, _category, _price, image_name) in enumerate(seed_calls[:20], start=1)
    ]
    return core


def _extract_catalog_descriptors(source: str) -> dict[str, dict[str, str]]:
    entries = re.findall(
        r'Map\.entry\("([^"]+)",\s*new CatalogDescriptor\("([^"]+)",\s*"([^"]+)"\)\)',
        source,
    )
    if not entries:
        raise RuntimeError("No CatalogDescriptor entries found in DataInitializer.java.")

    return {basename: {"name": name, "category": category} for basename, name, category in entries}


def build_prompt(product_name: str) -> str:
    return PROMPT_TEMPLATE.format(product_name=product_name)


def ensure_output_dir() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def write_manifest(products: Iterable[Product]) -> None:
    rows = []
    for product in products:
        rows.append(
            {
                "id": product.id,
                "name": product.name,
                "source_image_name": product.source_image_name,
                "output_filename": product.output_filename,
                "prompt": build_prompt(product.name),
            }
        )
    if pd is not None:
        df = pd.DataFrame(rows)
        df.to_csv(MANIFEST_CSV, index=False, encoding="utf-8-sig")
        return

    with MANIFEST_CSV.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def already_exists(product: Product) -> bool:
    return product.output_path.exists() and product.output_path.stat().st_size > 0


def estimate_cost(count: int) -> float:
    return round(count * PRICE_PER_IMAGE_USD, 2)


def generate_one(product: Product, api_key: str, model: str) -> tuple[str, str | None]:
    """
    Returns:
    - ("generated", None) on success
    - ("skipped", None) if the file already exists
    - ("failed", error_message) if generation fails after retries
    """
    if already_exists(product):
        return ("skipped", None)

    prompt = build_prompt(product.name)
    last_error: Exception | None = None

    for attempt in range(MAX_RETRIES + 1):
        try:
            client = get_client(api_key)
            response = client.images.generate(
                model=model,
                prompt=prompt,
                response_format="b64_json",
                extra_body={
                    "aspect_ratio": ASPECT_RATIO,
                    "resolution": RESOLUTION,
                },
            )

            image_bytes = extract_image_bytes(response)
            product.output_path.write_bytes(image_bytes)
            return ("generated", None)
        except Exception as exc:  # noqa: BLE001 - the API can fail in many ways
            last_error = exc
            if attempt >= MAX_RETRIES:
                break
            time.sleep(2 * (attempt + 1))

    return ("failed", f"{type(last_error).__name__}: {last_error}")


def extract_image_bytes(response) -> bytes:
    """Prefer base64 output, fall back to downloading the temporary URL if needed."""
    if not getattr(response, "data", None):
        raise RuntimeError("Image response did not contain a data array.")

    first = response.data[0]
    b64_payload = getattr(first, "b64_json", None)
    if b64_payload:
        return base64.b64decode(b64_payload)

    url = getattr(first, "url", None)
    if url:
        try:
            import requests
        except ImportError as exc:  # pragma: no cover - runtime dependency
            raise RuntimeError(
                "The 'requests' package is required for URL fallback downloads. "
                "Install dependencies first: pip install openai pandas tqdm requests python-dotenv"
            ) from exc
        downloaded = requests.get(url, timeout=60)
        downloaded.raise_for_status()
        return downloaded.content

    raise RuntimeError("Image response had neither b64_json nor url.")


def main() -> None:
    try:
        from tqdm import tqdm
    except ImportError as exc:  # pragma: no cover - runtime dependency
        raise SystemExit(
            "The 'tqdm' package is required. Install dependencies first: "
            "pip install openai pandas tqdm requests python-dotenv"
        ) from exc

    args = parse_args()
    if args.workers < 1:
        raise SystemExit("--workers must be >= 1")

    ensure_output_dir()
    api_key = load_api_key()
    products = extract_products()

    if args.limit is not None:
        products = products[: args.limit]

    write_manifest(products)

    skipped_products = [product for product in products if already_exists(product)]
    pending_products = [product for product in products if not already_exists(product)]

    print(f"Catalog products found: {len(products)}")
    print(f"Already existing images: {len(skipped_products)}")
    print(f"Images to generate now: {len(pending_products)}")
    print(f"Aspect ratio: {ASPECT_RATIO}")
    print(f"Model: {args.model}")
    print(f"Estimated run cost: ~${estimate_cost(len(pending_products)):.2f}")
    print(f"Estimated full 200-image catalog cost: ~${estimate_cost(len(products)):.2f}")
    print(f"Manifest written to: {MANIFEST_CSV}")

    started_at = time.perf_counter()
    generated = 0
    skipped = len(skipped_products)
    failed: list[tuple[Product, str]] = []

    if pending_products:
        with ThreadPoolExecutor(max_workers=args.workers) as executor:
            futures = {
                executor.submit(generate_one, product, api_key, args.model): product
                for product in pending_products
            }
            with tqdm(total=len(products), initial=skipped, desc="Generating product images", unit="img") as progress:
                for future in as_completed(futures):
                    product = futures[future]
                    try:
                        status, error_message = future.result()
                    except Exception as exc:  # noqa: BLE001
                        status, error_message = ("failed", f"{type(exc).__name__}: {exc}")

                    if status == "generated":
                        generated += 1
                    elif status == "skipped":
                        skipped += 1
                    else:
                        failed.append((product, error_message or "Unknown error"))

                    progress.update(1)
    else:
        print("Nothing to generate. All requested files already exist.")

    elapsed_seconds = time.perf_counter() - started_at
    print("\nGeneration summary")
    print(f"Generated: {generated}")
    print(f"Skipped:   {skipped}")
    print(f"Failed:    {len(failed)}")
    print(f"Elapsed:   {elapsed_seconds:.1f}s")
    print(f"Approx. run cost: ~${estimate_cost(generated):.2f}")

    if failed:
        print("\nFailed items:")
        for product, error in failed:
            print(f"- #{product.id:04d} {product.name}: {error}")


if __name__ == "__main__":
    main()
