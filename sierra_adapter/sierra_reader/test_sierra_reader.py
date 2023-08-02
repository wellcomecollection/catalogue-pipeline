import json
import unittest

from sierra_reader import to_scala_record


class TestToScalaRecord(unittest.TestCase):
    def test_it_handles_a_bib_record(self):
        record = {
            "author": "M'Kendrick, Archibald.",
            "createdDate": "1999-11-01T16:36:51Z",
            "fixedFields": {
                "107": {"label": "MARCTYPE", "value": " "},
                "24": {"display": "English", "label": "LANG", "value": "eng"},
            },
            "id": "1000002",
            "title": "An x-ray atlas of the normal and abnormal structures of the body",
            "updatedDate": "2022-07-21T20:01:39Z",
        }

        self.assertEqual(
            json.loads(to_scala_record(record)),
            {
                "id": record["id"],
                "data": json.dumps(record),
                "bibIds": [],
                "modifiedDate": record["updatedDate"],
            },
        )

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
                "modifiedDate": record["updatedDate"],
            },
        )


if __name__ == "__main__":
    unittest.main()
