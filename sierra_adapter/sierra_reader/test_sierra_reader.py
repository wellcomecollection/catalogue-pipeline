import json
import unittest

from sierra_reader import to_scala_record


class TestToScalaRecord(unittest.TestCase):
    def test_it_handles_an_order_record(self):
        record = {
            "bibs": [
                "https://libsys.wellcomelibrary.org/iii/sierra-api/v6/bibs/1320839"
            ],
            "fixedFields": {
                "1": {"label": "ACQ TYPE", "value": "d"},
                "2": {
                    "display": "Wellcome Library",
                    "label": "LOCATION",
                    "value": "acql ",
                },
            },
            "id": 1000951,
            "updatedDate": "2023-08-01T12:24:55Z",
        }

        self.assertEqual(
            json.loads(to_scala_record(record)),
            {
                "id": record["id"],
                "data": json.dumps(record),
                "bibIds": ["1320839"],
                "modifiedDate": "2023-08-01T12:24:55Z",
            },
        )


if __name__ == "__main__":
    unittest.main()
