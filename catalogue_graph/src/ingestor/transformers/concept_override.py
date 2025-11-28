import csv
import os
from functools import lru_cache
from typing import TextIO

from ingestor.models.display.id_label import DisplayIdLabel
from ingestor.models.display.location import DisplayDigitalLocation
from ingestor.models.indexable_concept import ConceptDescription

from .raw_concept import RawNeptuneConcept
from .raw_related_concepts import (
    RawNeptuneRelatedConcept,
)

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_PATH = os.path.join(HERE, "wellcome_collection_authority.csv")


@lru_cache
def load_csv_overrides(file_path: str) -> dict[str, dict[str, str]]:
    with open(file_path) as f:
        return load_overrides(f)


def load_overrides(overrides: TextIO) -> dict[str, dict[str, str]]:
    csv_reader = csv.DictReader(overrides)
    return {row["id"].strip(): row for row in csv_reader}


class ConceptTextOverrideProvider:
    overrides: dict[str, dict[str, str]] = {}

    def __init__(self, overrides_csv: TextIO | None = None):
        if overrides_csv:
            self.overrides = load_overrides(overrides_csv)
        else:
            self.overrides = load_csv_overrides(DEFAULT_PATH)

    def get_label_override(self, concept_id: str) -> str | None:
        override = self.overrides.get(concept_id)
        if override and (override_label := override["label"].strip()):
            return override_label

        return None

    def display_label_of(
        self, raw_concept: RawNeptuneConcept | RawNeptuneRelatedConcept
    ) -> str:
        override = self.get_label_override(raw_concept.wellcome_id)
        if override:
            return override

        return raw_concept.display_label

    def description_of(
        self, raw_concept: RawNeptuneConcept
    ) -> ConceptDescription | None:
        override = self.overrides.get(raw_concept.wellcome_id)
        if override:
            override_description = override["description"].strip()
            if override_description.lower() == "empty":
                return None
            if override_description:
                return ConceptDescription(
                    text=override_description,
                    sourceUrl=None,
                    sourceLabel="weco-authority",
                )
        return raw_concept.description

    def display_images(
        self, raw_concept: RawNeptuneConcept
    ) -> list[DisplayDigitalLocation]:
        override = self.overrides.get(raw_concept.wellcome_id)
        if override and (override_image_urls := override["image_url"].split("||")):
            return [
                DisplayDigitalLocation(
                    url=url.strip(),
                    locationType=DisplayIdLabel(
                        id="iiif-image", label="IIIF Image API", type="LocationType"
                    ),
                    accessConditions=[],
                )
                for url in override_image_urls
                if url.strip()  # Filter out empty URLs
            ]
        return []
