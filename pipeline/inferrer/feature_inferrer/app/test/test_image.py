import unittest
from src.image import get_image_url_from_iiif_url


class TestImage(unittest.TestCase):
    def test_iiif_parse_dlcs(self):
        image_id = "b28047345_0009.jp2"
        test_url = f"https://dlcs.io/iiif-img/wellcome/5/{image_id}/info.json"
        result = get_image_url_from_iiif_url(test_url)
        expected = (
            f"https://dlcs.io/thumbs/wellcome/5/{image_id}/full/!400,400/0/default.jpg"
        )
        self.assertEqual(result, expected)

    def test_iiif_parse_other(self):
        image_id = "V0002882.jpg"
        test_url = f"https://iiif.wellcomecollection.org/image/{image_id}/info.json"
        result = get_image_url_from_iiif_url(test_url)
        expected = f"https://iiif.wellcomecollection.org/image/{image_id}/full/224,224/0/default.jpg"
        self.assertEqual(result, expected)

    def test_iiif_parse_invalid(self):
        test_url = "https://example.com/pictures/123/skateboard.png"
        with self.assertRaises(ValueError):
            get_image_url_from_iiif_url(test_url)


if __name__ == "__main__":
    unittest.main()
