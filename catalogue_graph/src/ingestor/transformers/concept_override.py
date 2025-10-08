import csv
import os
from typing import TextIO

from ingestor.models.display.id_label import DisplayIdLabel
from ingestor.models.display.location import DisplayDigitalLocation
from ingestor.models.indexable_concept import ConceptDescription

from .raw_concept import RawNeptuneConcept
from .raw_related_concepts import (
    RawNeptuneRelatedConcept,
)

HERE = os.path.dirname(os.path.abspath(__file__))


class ConceptTextOverrideProvider:
    overrides: dict[str, dict[str, str]] = {}

    def __init__(self, overrides_csv: TextIO | None):
        if overrides_csv:
            self._load_overrides(overrides_csv)
        else:
            with open(
                os.path.join(HERE, "wellcome_collection_authority.csv")
            ) as csv_file:
                self._load_overrides(csv_file)

    def _load_overrides(self, overrides: TextIO) -> None:
        csv_reader = csv.DictReader(overrides)
        self.overrides = {row["id"].strip(): row for row in csv_reader}

    def display_label_of(
        self, raw_concept: RawNeptuneConcept | RawNeptuneRelatedConcept
    ) -> str:
        override = self.overrides.get(raw_concept.wellcome_id)
        if override and (override_label := override["label"].strip()):
            return override_label
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
                    text=override_description, sourceUrl=None, sourceLabel=None
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
