import unittest

from generate_200_product_images import (
    ASPECT_RATIO,
    Product,
    build_prompt,
    parse_selected_ids,
    select_products,
)


class GenerateProductImagesTests(unittest.TestCase):
    def test_select_products_supports_name_filters(self):
        products = [
            Product(221, "Лечо домашнее 700 г", "Консервация", "lecho.webp"),
            Product(
                245,
                "Масло подсолнечное нерафинированное 1 л",
                "Масла",
                "sunflower-oil-unrefined.webp",
            ),
            Product(250, "Икра кабачковая 500 г", "Консервация", "squash-caviar.webp"),
        ]

        selected = select_products(
            products,
            selected_ids=parse_selected_ids("245"),
            selected_names={
                "лечо домашнее 700 г",
                "масло подсолнечное нерафинированное 1 л",
            },
            limit=None,
        )

        self.assertEqual([product.id for product in selected], [221, 245])

    def test_prompt_requires_pure_white_uncropped_packshot(self):
        prompt = build_prompt(
            Product(221, "Лечо домашнее 700 г", "Консервация", "lecho.webp")
        )

        self.assertIn("pure white seamless studio background", prompt)
        self.assertIn("entire product fully visible", prompt)
        self.assertIn("do not crop the subject", prompt)
        self.assertIn("strict aspect ratio 2:1", prompt)

    def test_aspect_ratio_is_updated_for_wide_catalog_cards(self):
        self.assertEqual(ASPECT_RATIO, "2:1")


if __name__ == "__main__":
    unittest.main()
