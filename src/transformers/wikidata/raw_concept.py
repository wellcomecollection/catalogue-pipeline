from typing import Literal
import re
from functools import lru_cache

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


class RawWikidataConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept

    def _extract_field_value(self, field_name: str) -> str:
        field = self.raw_concept[field_name]
        assert field["type"] == "literal", self.raw_concept

        return field["value"]

    def _extract_optional_field_value(self, field_name: str) -> str | None:
        if field_name not in self.raw_concept:
            return None

        return self._extract_field_value(field_name)

    def _extract_english_field_value(self, field_name: str):
        assert self.raw_concept[field_name]["xml:lang"] == "en"
        return self._extract_field_value(field_name)

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
        # TODO: Handle non-English labels
        return self._extract_field_value("itemLabel")

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


class RawWikidataLocation(RawWikidataConcept):
    @lru_cache
    def _get_coordinates(self) -> dict:
        """Extracts coordinates from a raw string in the format `Point(<float> <float>)` (e.g. `Point(9.83 53.54)`)"""
        # Some items do not return valid coordinates (e.g. Q17064702, whose coordinates just say 'unknown value' on the
        # Wikidata website). When this happens, the 'type' of the 'coordinates' property always appears to be 'uri'.
        if self.raw_concept["coordinates"]["type"] == "uri":
            return {"longitude": None, "latitude": None}

        raw_coordinates = self._extract_field_value("coordinates")

        pattern = r"Point\((.*)\s(.*)\)"
        matched_coordinates = re.search(pattern, raw_coordinates)

        assert (
            matched_coordinates is not None
        ), f"Could not extract coordinates from raw value '{raw_coordinates}'. Wikidata id: {self.source_id}"

        longitude = float(matched_coordinates.group(1))
        latitude = float(matched_coordinates.group(2))
        return {"longitude": longitude, "latitude": latitude}

    @property
    def longitude(self) -> float | None:
        return self._get_coordinates()["longitude"]

    @property
    def latitude(self) -> float | None:
        return self._get_coordinates()["latitude"]


class RawWikidataName(RawWikidataConcept):
    def _extract_date(self, field_name: str):
        date_value = self._extract_optional_field_value(field_name)

        # When a date is unknown, sometimes Wikidata returns a URL instead of a valid date, such as
        # 'http://www.wikidata.org/.well-known/genid/42feb541ed97156abba749622d33f2d9'. When this happens, return None.
        if date_value is None or date_value.startswith("http"):
            return None

        return date_value

    @property
    def date_of_birth(self) -> str | None:
        return self._extract_date("dateOfBirthLabel")

    @property
    def date_of_death(self) -> str | None:
        return self._extract_date("dateOfDeathLabel")

    @property
    def place_of_birth(self) -> str | None:
        return self._extract_optional_field_value("placeOfBirthLabel")
