from typing import Literal

# {
#     "country": {"type": "uri", "value": "http://www.wikidata.org/entity/Q183"},
#     "countryLabel": {"xml:lang": "en", "type": "literal", "value": "Germany"},
#     "countryDescription": {
#         "xml:lang": "en",
#         "type": "literal",
#         "value": "country in Central Europe",
#     },
#     "countryAltLabel": {
#         "xml:lang": "en",
#         "type": "literal",
#         "value": "BR Deutschland, Bundesrepublik Deutschland, Deutschland, Federal Republic of Germany",
#     },
#     "coordinate_location": {
#         "datatype": "http://www.opengis.net/ont/geosparql#wktLiteral",
#         "type": "literal",
#         "value": "Point(9.83 53.54)",
#     },
# }

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


class RawWikidataConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept

    def _extract_english_field_value(self, field_name: str):
        field = self.raw_concept[field_name]

        # assert field["xml:lang"] == "en"
        assert field["type"] == "literal"

        return field["value"]

    @staticmethod
    def _remove_id_prefix(raw_id: str) -> str:
        return raw_id.removeprefix(WIKIDATA_ID_PREFIX)

    @property
    def source_id(self) -> str:
        item_field = self.raw_concept["item"]
        assert item_field["type"] == "uri"
        return self._remove_id_prefix(item_field["value"])

    @property
    def label(self) -> str:
        return self._extract_english_field_value("itemLabel")

    @property
    def alternative_labels(self) -> list[str]:
        """Returns a list of alternative labels for the concept."""
        if "itemAltLabel" not in self.raw_concept:
            return []

        raw_alternative_labels = self._extract_english_field_value("itemAltLabel")
        return raw_alternative_labels.split(", ")

    @property
    def description(self) -> str | None:
        if "itemDescription" not in self.raw_concept:
            return None

        return self._extract_english_field_value("itemDescription")

    @property
    def source(self) -> Literal["wikidata"]:
        return "wikidata"
