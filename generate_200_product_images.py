#!/usr/bin/env python3
"""
Generate Farm Sales product images through the xAI Imagine API.

This version is stricter than the original bulk generator:
- It reconstructs the seeded catalog from the repo resources.
- It infers the sold form of each SKU before building the prompt.
- It can overwrite existing renders and sync the final JPGs into the frontend.

The key fix is semantic disambiguation. A product like "Кролик фермерский 1 кг"
must render as a butcher-ready food product, not as a live rabbit.
"""

from __future__ import annotations

import argparse
import base64
import csv
import getpass
import os
import re
import shutil
import threading
import time
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
CATALOG_IMAGE_LIST = PROJECT_ROOT / "backend" / "src" / "main" / "resources" / "catalog-product-images.txt"
PUBLIC_PRODUCTS_DIR = PROJECT_ROOT / "frontend" / "public" / "images" / "products"
OUTPUT_DIR = PROJECT_ROOT / "product_images"
MANIFEST_CSV = OUTPUT_DIR / "products_manifest.csv"

XAI_BASE_URL = "https://api.x.ai/v1"
DEFAULT_MODEL = "grok-imagine-image"
DEFAULT_WORKERS = 14
MAX_RETRIES = 2
PRICE_PER_IMAGE_USD = 0.02
ASPECT_RATIO = "2:1"
RESOLUTION = "1k"

COMMON_AVOID = (
    "text",
    "captions",
    "labels",
    "stickers",
    "logos",
    "branding",
    "watermarks",
    "people",
    "hands",
    "props",
    "cutlery",
    "plates",
    "recipe styling",
    "restaurant plating",
    "kitchen scene",
    "table scene",
    "farm scene",
    "outdoor background",
    "wire rack",
    "grid background",
    "tile wall",
    "countertop",
    "tabletop",
    "shelf",
    "collage",
    "border",
    "background clutter",
)

_THREAD_LOCAL = threading.local()


@dataclass(frozen=True, slots=True)
class Product:
    id: int
    name: str
    category: str
    source_image_name: str

    @property
    def output_filename(self) -> str:
        return f"product_{self.id:04d}.jpg"

    @property
    def output_path(self) -> Path:
        return OUTPUT_DIR / self.output_filename

    @property
    def public_numbered_path(self) -> Path:
        return PUBLIC_PRODUCTS_DIR / self.output_filename

    @property
    def public_alias_filename(self) -> str:
        return Path(self.source_image_name).with_suffix(".jpg").name

    @property
    def public_alias_path(self) -> Path:
        return PUBLIC_PRODUCTS_DIR / self.public_alias_filename


@dataclass(frozen=True, slots=True)
class PromptProfile:
    subject: str
    packaging: str
    notes: str
    avoid: tuple[str, ...] = ()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate semantically correct Farm Sales product images with the xAI Imagine API."
    )
    parser.add_argument("--workers", type=int, default=DEFAULT_WORKERS, help="Parallel worker count (default: 14)")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="xAI image model (default: grok-imagine-image)")
    parser.add_argument("--limit", type=int, default=None, help="Optional debug limit for the number of products")
    parser.add_argument("--ids", default="", help="Comma-separated product ids to process, for example 62,192,200")
    parser.add_argument(
        "--names",
        default="",
        help="Comma-separated product names to process, for example \"Лечо домашнее 700 г,Масло подсолнечное нерафинированное 1 л\"",
    )
    parser.add_argument("--overwrite", action="store_true", help="Regenerate images even if the JPG already exists")
    parser.add_argument(
        "--sync-public",
        action="store_true",
        help="Copy generated JPGs into frontend/public/images/products as both numbered and alias files",
    )
    parser.add_argument(
        "--dry-run-prompts",
        action="store_true",
        help="Print prompts for the selected products instead of generating images",
    )
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
    Reproduce the same canonical catalog order used by the application:
    - first 20 products are explicit seedProduct(...) calls
    - remaining products come from the bundled catalog image list resource
    """
    if not DATA_INITIALIZER.exists():
        raise FileNotFoundError(f"Missing DataInitializer.java: {DATA_INITIALIZER}")
    if not CATALOG_IMAGE_LIST.exists():
        raise FileNotFoundError(f"Missing catalog image list: {CATALOG_IMAGE_LIST}")

    source = DATA_INITIALIZER.read_text(encoding="utf-8")

    core_products = _extract_core_products(source)
    descriptor_map = _extract_catalog_descriptors(source)

    core_image_names = {product.source_image_name for product in core_products}
    supplemental_image_names = [
        image_name
        for image_name in _load_catalog_image_names()
        if image_name not in core_image_names
    ]
    if not supplemental_image_names:
        raise RuntimeError("Bundled catalog image list did not provide supplemental items.")

    supplemental_products: list[Product] = []
    for offset, image_name in enumerate(supplemental_image_names, start=len(core_products) + 1):
        basename = Path(image_name).stem
        descriptor = descriptor_map.get(basename)
        if descriptor is None:
            raise RuntimeError(f"Missing CatalogDescriptor for supplemental image '{image_name}'.")
        supplemental_products.append(
            Product(
                id=offset,
                name=descriptor["name"],
                category=descriptor["category"],
                source_image_name=image_name,
            )
        )

    products = core_products + supplemental_products
    expected_total = len(core_products) + len(supplemental_image_names)
    if len(products) != expected_total:
        raise RuntimeError(f"Expected exactly {expected_total} products, reconstructed {len(products)}.")

    return products


def _extract_core_products(source: str) -> list[Product]:
    seed_calls = re.findall(
        r'seedProduct\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*\d+,\s*"([^"]+)"\);',
        source,
    )
    if len(seed_calls) < 20:
        raise RuntimeError(f"Expected at least 20 seedProduct calls, found {len(seed_calls)}.")

    return [
        Product(
            id=index,
            name=name,
            category=category,
            source_image_name=image_name,
        )
        for index, (name, category, _price, image_name) in enumerate(seed_calls[:20], start=1)
    ]


def _extract_catalog_descriptors(source: str) -> dict[str, dict[str, str]]:
    entries = re.findall(
        r'Map\.entry\("([^"]+)",\s*new CatalogDescriptor\("([^"]+)",\s*"([^"]+)"\)\)',
        source,
    )
    if not entries:
        raise RuntimeError("No CatalogDescriptor entries found in DataInitializer.java.")

    return {basename: {"name": name, "category": category} for basename, name, category in entries}


def _load_catalog_image_names() -> list[str]:
    image_names: list[str] = []
    seen: set[str] = set()
    for raw_line in CATALOG_IMAGE_LIST.read_text(encoding="utf-8").splitlines():
        image_name = raw_line.strip()
        if not image_name or image_name in seen:
            continue
        seen.add(image_name)
        image_names.append(image_name)
    return image_names


def parse_selected_ids(raw_ids: str) -> set[int] | None:
    raw_ids = raw_ids.strip()
    if not raw_ids:
        return None
    selected: set[int] = set()
    for chunk in raw_ids.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        selected.add(int(chunk))
    if not selected:
        return None
    return selected


def parse_selected_names(raw_names: str) -> set[str] | None:
    raw_names = raw_names.strip()
    if not raw_names:
        return None

    selected = {
        normalize_text(chunk)
        for chunk in raw_names.split(",")
        if normalize_text(chunk)
    }
    return selected or None


def select_products(
    products: list[Product],
    selected_ids: set[int] | None,
    selected_names: set[str] | None,
    limit: int | None,
) -> list[Product]:
    selected = []
    for product in products:
        if selected_ids is not None and product.id in selected_ids:
            selected.append(product)
            continue
        if selected_names is not None and normalize_text(product.name) in selected_names:
            selected.append(product)

    if selected_ids is None and selected_names is None:
        selected = list(products)

    if limit is not None:
        selected = selected[:limit]
    return selected


def normalize_text(value: str) -> str:
    return value.casefold().replace("ё", "е").strip()


def has_any(text: str, needles: Iterable[str]) -> bool:
    return any(needle in text for needle in needles)


def detect_meat_animal(name: str) -> str:
    mapping = (
        ("говяж", "beef"),
        ("телят", "veal"),
        ("свин", "pork"),
        ("кур", "chicken"),
        ("индей", "turkey"),
        ("утк", "duck"),
        ("крол", "rabbit"),
        ("баран", "lamb"),
    )
    for needle, label in mapping:
        if needle in name:
            return label
    return "meat"


def detect_meat_cut(name: str) -> str:
    mapping = (
        ("фарш", "ground meat"),
        ("бедро", "thigh cut"),
        ("голень", "drumstick cut"),
        ("крыл", "wing portion"),
        ("филе", "fillet cut"),
        ("груд", "breast cut"),
        ("печень", "liver portion"),
        ("сердеч", "hearts portion"),
        ("ребр", "rib cut"),
        ("лопат", "shoulder cut"),
        ("шея", "neck cut"),
        ("грудин", "brisket cut"),
        ("карбонад", "loin cut"),
        ("окорок", "ham cut"),
        ("вырез", "tenderloin cut"),
        ("тазобедрен", "leg cut"),
        ("грудинк", "belly cut"),
        ("гуляш", "goulash cubes"),
        ("стейк", "steak cut"),
        ("шницел", "schnitzel cut"),
    )
    for needle, label in mapping:
        if needle in name:
            return label
    return "butcher cut"


def classify_product(product: Product) -> PromptProfile:
    name = normalize_text(product.name)
    category = normalize_text(product.category)

    if "яиц" in name or "яйц" in name:
        egg_subject = "open plain paper egg carton filled with fresh eggs"
        if "перепел" in name:
            egg_subject = "open plain quail egg tray filled with fresh quail eggs"
        return PromptProfile(
            subject=egg_subject,
            packaging="retail-ready food product packshot",
            notes="show the exact sold food product only; not birds; not a nest",
            avoid=("live bird", "chicken", "quail bird", "nest", "farmyard"),
        )

    if "крол" in name:
        return PromptProfile(
            subject="raw dressed rabbit carcass, skinned, headless, butcher-ready meat product",
            packaging="single carcass on a clean white surface",
            notes="food retail product only; absolutely not a live rabbit",
            avoid=("live rabbit", "pet rabbit", "fur", "grass", "farm animal portrait"),
        )

    if "утк" in name and "фарш" not in name:
        return PromptProfile(
            subject="raw dressed duck carcass, plucked, headless, butcher-ready meat product",
            packaging="single carcass on a clean white surface",
            notes="food retail product only; absolutely not a live duck",
            avoid=("live duck", "feathers", "pond", "grass", "farm animal portrait"),
        )

    if "куриц" in name and "фарш" not in name and "филе" not in name and "бедр" not in name and "голен" not in name and "крыл" not in name and "печен" not in name and "сердеч" not in name:
        return PromptProfile(
            subject="raw dressed whole chicken carcass, plucked, headless, butcher-ready meat product",
            packaging="single carcass on a clean white surface",
            notes="food retail product only; absolutely not a live chicken",
            avoid=("live chicken", "feathers", "farm animal portrait", "grass", "coop"),
        )

    if has_any(name, ("фарш", "филе", "бедр", "голен", "крыл", "печен", "сердеч", "ребр", "лопат", "шея", "грудин", "карбонад", "окорок", "вырез", "тазобедрен", "грудинк", "гуляш", "стейк", "шницел", "говяж", "свин", "телят", "индей", "курин", "баран")) and not has_any(name, ("котлет", "голубц", "фрикадел", "вареник", "мант")):
        animal = detect_meat_animal(name)
        cut = detect_meat_cut(name)
        if "фарш" in name:
            return PromptProfile(
                subject=f"raw {animal} {cut}",
                packaging="presented in a plain black meat tray with no label or sticker",
                notes="uncooked butcher product; no seasoning; not a prepared dish",
                avoid=("live animal", "farm animal portrait", "grass", "cooked food"),
            )
        if has_any(name, ("колбас", "купат")):
            return PromptProfile(
                subject=f"raw {animal} sausage links",
                packaging="presented in a plain black meat tray with no label or sticker",
                notes="uncooked retail meat product; not grilled; not plated",
                avoid=("live animal", "grill", "pan", "restaurant plating"),
            )
        if "пельмен" in name:
            return PromptProfile(
                subject="raw frozen dumplings",
                packaging="presented as a clean retail frozen-food product packshot",
                notes="uncooked product only; not served in a bowl",
                avoid=("soup", "plate", "fork", "restaurant plating"),
            )
        return PromptProfile(
            subject=f"raw {animal} {cut}, butcher-ready food product",
            packaging="single clean meat cut on a white surface",
            notes="uncooked retail meat product; no seasoning; not a prepared dish",
            avoid=("live animal", "farm animal portrait", "grass", "cooked food"),
        )

    if has_any(name, ("пельмен", "вареник", "мант")):
        if "пельмен" in name:
            return PromptProfile(
                subject="raw frozen dumplings",
                packaging="presented as a clean retail frozen-food product packshot",
                notes="uncooked product only; not served in a bowl",
                avoid=("soup", "plate", "fork", "restaurant plating"),
            )
        if "вареник" in name:
            return PromptProfile(
                subject="raw frozen vareniki dumplings",
                packaging="plain frozen-food pouch or tray with no label and no text",
                notes="uncooked retail frozen product only; not boiled; not served",
                avoid=("plate", "fork", "sour cream serving", "restaurant plating"),
            )
        return PromptProfile(
            subject="raw frozen manti dumplings",
            packaging="plain frozen-food tray or pouch with no label and no text",
            notes="uncooked retail frozen product only; not steamed; not served",
            avoid=("plate", "fork", "restaurant plating", "sauce"),
        )

    if has_any(name, ("колбас", "купат")):
        return PromptProfile(
            subject="raw sausage links",
            packaging="presented in a plain black meat tray with no label or sticker",
            notes="uncooked retail meat product; not grilled; not plated",
            avoid=("grill", "pan", "restaurant plating"),
        )

    if has_any(name, ("котлет", "голубц", "фрикадел")):
        return PromptProfile(
            subject=f"raw frozen semi-finished product: {product.name}",
            packaging="plain black tray or clear frozen-food pouch with no label and no text",
            notes="uncooked retail полуфабрикат only; not plated; not cooked",
            avoid=("plate", "fork", "sauce", "restaurant plating", "cooked food"),
        )

    if has_any(name, ("мед", "джем", "варень", "квашен", "марин", "лечо", "икра кабач")):
        return PromptProfile(
            subject=f"unbranded clear glass jar of {product.name}",
            packaging="sealed plain grocery jar with no label and no sticker",
            notes="show the sold grocery jar only",
            avoid=("bees", "hive", "honeycomb", "breakfast table", "toast"),
        )

    if has_any(name, ("сок", "морс", "компот", "вода", "квас", "сироп", "масло подсолнеч", "масло льня", "масло рапсов", "масло горч", "масло кукуруз", "масло")) and not has_any(name, ("сливоч", "топлен")):
        return PromptProfile(
            subject=f"unbranded retail bottle of {product.name}",
            packaging="plain grocery bottle with no label and no text",
            notes="show the sold beverage or oil bottle only",
            avoid=("farm animal", "orchard scene", "glass pouring splash", "meal setup"),
        )

    if has_any(name, ("молоко", "кефир", "ряженк", "простокваш", "пахт", "сыворотк")):
        return PromptProfile(
            subject=f"plain unbranded dairy bottle of {product.name}",
            packaging="clean retail dairy bottle with no label and no text",
            notes="show the sold dairy product only",
            avoid=("cow", "barn", "meadow", "milk splash", "breakfast table"),
        )

    if "йогурт питьев" in name:
        return PromptProfile(
            subject=f"plain unbranded drinkable yogurt bottle of {product.name}",
            packaging="clean retail bottle with no label and no text",
            notes="show the sold dairy bottle only",
            avoid=("cow", "barn", "fruit explosion", "breakfast bowl"),
        )

    if has_any(name, ("йогурт", "сметан", "творог", "рикотт", "десерт творож", "сыр творож", "творожн", "сливк")):
        return PromptProfile(
            subject=f"plain unbranded dairy container of {product.name}",
            packaging="simple white or clear retail tub with no label and no text",
            notes="show the sold dairy container only",
            avoid=("cow", "barn", "breakfast table", "spoon serving"),
        )

    if has_any(name, ("масло сливоч", "масло крестьян")):
        return PromptProfile(
            subject=f"retail butter block of {product.name}",
            packaging="plain parchment-wrapped butter with no label and no text",
            notes="show the sold butter product only",
            avoid=("cow", "bread slice", "knife", "breakfast table"),
        )

    if has_any(name, ("масло топлен", "гхи")):
        return PromptProfile(
            subject=f"clear glass jar of {product.name}",
            packaging="plain grocery jar with no label and no text",
            notes="show the sold jar only",
            avoid=("cow", "pan cooking scene", "meal setup"),
        )

    if has_any(name, ("сыр", "брынз", "адыгей", "рикотт")) and "сыр творож" not in name:
        return PromptProfile(
            subject=f"retail cheese block or wedge of {product.name}",
            packaging="clean food product packshot with no label and no text",
            notes="show the sold cheese product only",
            avoid=("cow", "charcuterie board", "wine setup", "restaurant plating"),
        )

    if has_any(name, ("греч", "рис", "чечев", "пшен", "фасол", "нут", "горох", "круп", "мук", "хлоп", "манн", "семен", "семеч")) or has_any(category, ("круп", "бобов")):
        package = "standing plain kraft paper grocery bag with no label or text"
        if has_any(name, ("рис", "фасол", "чечев", "горох", "нут", "семеч", "семен")):
            package = "standing clear unbranded grocery pouch with no label or text"
        return PromptProfile(
            subject=f"retail package of {product.name}",
            packaging=package,
            notes="show the sold grocery package only",
            avoid=("field", "harvest scene", "wooden scoop", "meal bowl"),
        )

    if has_any(name, ("хлеб", "батон", "булк", "багет", "лаваш", "лепеш", "лепёш", "сухар")) or has_any(category, ("хлеб", "выпеч")):
        return PromptProfile(
            subject=f"fresh bakery product: {product.name}",
            packaging="single bakery item only, clean catalog packshot",
            notes="show the sold bread or pastry item only",
            avoid=("cutlery", "plate", "table setting", "sandwich"),
        )

    if has_any(name, ("клубник", "малин", "голубик", "смородин", "клюкв", "ежевик", "брусник", "вишн", "черешн", "крыжовн", "облепих", "черник")) or has_any(category, ("ягод",)):
        return PromptProfile(
            subject=f"fresh retail berry pack of {product.name}",
            packaging="clear unbranded punnet or small berry tray with no label and no text",
            notes="show the sold berry product only",
            avoid=("garden scene", "branch", "human hand", "dessert"),
        )

    if has_any(name, ("укроп", "петруш", "базилик", "шпинат", "салат", "ромэн", "смесь салат", "кинз", "руккол", "сельдер", "щав")) or has_any(category, ("зелень",)):
        return PromptProfile(
            subject=f"fresh herb or leafy green product: {product.name}",
            packaging="single bunch or head only, clean catalog packshot",
            notes="show the sold leafy product only",
            avoid=("salad bowl", "fork", "kitchen scene", "plate"),
        )

    if has_any(category, ("овощ", "фрукт")) or has_any(name, ("картоф", "морков", "лук", "огур", "томат", "яблок", "груш", "слив", "перец", "капуст", "свекл", "кабач", "тыкв", "баклаж", "чеснок", "редис", "пастернак", "сельдер", "репа")):
        return PromptProfile(
            subject=f"fresh produce product: {product.name}",
            packaging="single produce item or clean grouped portion only, retail grocery packshot",
            notes="show the sold raw produce only",
            avoid=("farm field", "tree branch", "basket scene", "prepared salad"),
        )

    return PromptProfile(
        subject=f"retail grocery product: {product.name}",
        packaging="correct sold form for a grocery wholesale catalog packshot",
        notes="show the sold product only",
    )


def build_prompt(product: Product) -> str:
    profile = classify_product(product)
    avoid = list(dict.fromkeys((*profile.avoid, *COMMON_AVOID)))
    return (
        "Use case: product-mockup. "
        "Asset type: grocery wholesale catalog product photo. "
        f'Product name reference: "{product.name}". '
        f"Category reference: {product.category}. "
        f"Primary request: {profile.subject}. "
        "Scene/backdrop: pure white seamless studio background. "
        f"Subject: {profile.subject}. Packaging/presentation: {profile.packaging}. "
        "Style/medium: professional photorealistic studio product photography. "
        "Composition/framing: single centered product, clean isolated packshot, entire product fully visible, "
        "consistent catalog angle, wide commercial catalog framing, do not crop the subject, leave a small safe margin around the product, no empty scene. "
        "Lighting/mood: soft natural studio lighting, neutral color balance, premium commercial e-commerce quality, "
        "highly detailed realistic textures. "
        f"Constraints: depict the item exactly in the form sold to grocery stores; {profile.notes}; "
        f"strict aspect ratio {ASPECT_RATIO}. "
        f"Avoid: {', '.join(avoid)}."
    )


def ensure_output_dir(sync_public: bool) -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if sync_public:
        PUBLIC_PRODUCTS_DIR.mkdir(parents=True, exist_ok=True)


def write_manifest(products: Iterable[Product]) -> None:
    rows = []
    for product in products:
        rows.append(
            {
                "id": product.id,
                "name": product.name,
                "category": product.category,
                "source_image_name": product.source_image_name,
                "output_filename": product.output_filename,
                "public_alias_filename": product.public_alias_filename,
                "prompt": build_prompt(product),
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


def already_exists(product: Product, overwrite: bool) -> bool:
    if overwrite:
        return False
    return product.output_path.exists() and product.output_path.stat().st_size > 0


def estimate_cost(count: int) -> float:
    return round(count * PRICE_PER_IMAGE_USD, 2)


def generate_one(product: Product, api_key: str, model: str, overwrite: bool) -> tuple[str, str | None]:
    """
    Returns:
    - ("generated", None) on success
    - ("skipped", None) if the file already exists
    - ("failed", error_message) if generation fails after retries
    """
    if already_exists(product, overwrite):
        return ("skipped", None)

    prompt = build_prompt(product)
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
        except Exception as exc:  # noqa: BLE001
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


def sync_public_images(products: Iterable[Product]) -> None:
    for product in products:
        if not product.output_path.exists():
            continue
        shutil.copy2(product.output_path, product.public_numbered_path)
        shutil.copy2(product.output_path, product.public_alias_path)


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

    selected_ids = parse_selected_ids(args.ids)
    selected_names = parse_selected_names(args.names)
    ensure_output_dir(args.sync_public)
    products = select_products(extract_products(), selected_ids, selected_names, args.limit)
    if not products:
        raise SystemExit("No products matched the requested ids/limit.")

    write_manifest(products)

    if args.dry_run_prompts:
        for product in products:
            print(f"#{product.id:04d} {product.name} [{product.category}]")
            print(build_prompt(product))
            print()
        return

    api_key = load_api_key()
    skipped_products = [product for product in products if already_exists(product, args.overwrite)]
    pending_products = [product for product in products if not already_exists(product, args.overwrite)]

    print(f"Catalog products selected: {len(products)}")
    print(f"Already existing images: {len(skipped_products)}")
    print(f"Images to generate now: {len(pending_products)}")
    print(f"Aspect ratio: {ASPECT_RATIO}")
    print(f"Resolution: {RESOLUTION}")
    print(f"Model: {args.model}")
    print(f"Estimated run cost: ~${estimate_cost(len(pending_products)):.2f}")
    print(f"Manifest written to: {MANIFEST_CSV}")

    started_at = time.perf_counter()
    generated = 0
    skipped = len(skipped_products)
    failed: list[tuple[Product, str]] = []

    if pending_products:
        with ThreadPoolExecutor(max_workers=args.workers) as executor:
            futures = {
                executor.submit(generate_one, product, api_key, args.model, args.overwrite): product
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

    if args.sync_public:
        sync_public_images(products)
        print(f"Synced {len(products)} selected images into {PUBLIC_PRODUCTS_DIR}")

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
