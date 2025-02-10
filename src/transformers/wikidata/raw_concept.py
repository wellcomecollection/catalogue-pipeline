import re
from typing import Literal, TypedDict

from sources.wikidata.linked_ontology_source import extract_wikidata_id


class Coordinates(TypedDict):
    longitude: float | None
    latitude: float | None


class RawWikidataConcept:
    def __init__(self, raw_concept: dict):
        self.raw_concept = raw_concept

    def _extract_field_value(self, field_name: str) -> str:
        field = self.raw_concept[field_name]
        assert field["type"] == "literal", self.raw_concept
        assert isinstance(field["value"], str)

        return field["value"]

    def _extract_optional_field_value(self, field_name: str) -> str | None:
        if field_name not in self.raw_concept:
            return None

        return self._extract_field_value(field_name)

    def _extract_english_field_value(self, field_name: str) -> str:
        assert self.raw_concept[field_name]["xml:lang"] == "en"
        return self._extract_field_value(field_name)

    @property
    def source_id(self) -> str:
        wikidata_id = extract_wikidata_id(self.raw_concept)
        assert wikidata_id is not None
        return wikidata_id

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
    def _extract_coordinates(self) -> Coordinates:
        """Extracts coordinates from a raw string in the format `Point(<float> <float>)` (e.g. `Point(9.83 53.54)`)"""
        # Some items do not return valid coordinates (e.g. Q17064702, whose coordinates just say 'unknown value' on the
        # Wikidata website). When this happens, the 'type' of the 'coordinates' property always appears to be 'uri'.
        if (
            "coordinates" not in self.raw_concept
            or self.raw_concept["coordinates"]["type"] == "uri"
        ):
            return {"longitude": None, "latitude": None}

        raw_coordinates = self._extract_field_value("coordinates")

        pattern = r"Point\((.*)\s(.*)\)"
        matched_coordinates = re.search(pattern, raw_coordinates)

        assert matched_coordinates is not None, (
            f"Could not extract coordinates from raw value '{raw_coordinates}'. Wikidata id: {self.source_id}"
        )

        longitude = float(matched_coordinates.group(1))
        latitude = float(matched_coordinates.group(2))
        return {"longitude": longitude, "latitude": latitude}

    @property
    def coordinates(self) -> Coordinates:
        return self._extract_coordinates()


class RawWikidataName(RawWikidataConcept):
    def _extract_date(self, field_name: str) -> str | None:
        # Some Wikidata items store invalid dates of type 'uri', such as https://www.wikidata.org/wiki/Q20760409
        if (
            field_name in self.raw_concept
            and self.raw_concept[field_name]["type"] == "uri"
        ):
            return None

        date_value = self._extract_optional_field_value(field_name)

        # When a date is unknown, sometimes Wikidata returns a URL instead of a valid date, such as
        # 'http://www.wikidata.org/.well-known/genid/42feb541ed97156abba749622d33f2d9'. When this happens, return None.
        if date_value is None or date_value.startswith("http"):
            return None

        # There is currently one case where a Wikidata item stores an invalid date (Wikidata id: Q10904907). The date
        # is correctly formatted but references year 0, which does not exist. Neptune would throw an error if we tried
        # to load it in, so we filter it out.
        if date_value == "+0000-00-00T00:00:00Z":
            return None

        return date_value

    @property
    def date_of_birth(self) -> str | None:
        return self._extract_date("dateOfBirth")

    @property
    def date_of_death(self) -> str | None:
        return self._extract_date("dateOfDeath")

    @property
    def place_of_birth(self) -> str | None:
        return self._extract_optional_field_value("placeOfBirthLabel")
