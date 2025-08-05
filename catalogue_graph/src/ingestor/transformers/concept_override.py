import os
import csv

from typing import TextIO

from ingestor.models.indexable_concept import ConceptDescription

HERE = os.path.dirname(os.path.abspath(__file__))


class ConceptTextOverrider:
    overrides = None

    def __init__(self, overrides_csv: TextIO | None):
        if overrides_csv:
            self.load_overrides(overrides_csv)
        else:
            with open(os.path.join(HERE, "label_description_overrides.csv")) as csv_file:
                self.load_overrides(csv_file)

    def load_overrides(self, overrides: TextIO):
        csv_reader = csv.DictReader(overrides)
        self.overrides = {row['id'].strip(): row for row in csv_reader}

    def display_label_of(self, raw_concept) -> str:
        override = self.overrides.get(raw_concept.wellcome_id)
        if override and (override_label := override['label'].strip()):
            return override_label
        return raw_concept.display_label

    def description_of(self, raw_concept) -> ConceptDescription | None:
        override = self.overrides.get(raw_concept.wellcome_id)
        if override:
            if override_description := override['description'].strip():
                if override_description.lower() == 'empty':
                    return None
                return ConceptDescription(
                    text=override_description,
                    sourceLabel="wellcome",
                    sourceUrl=""
                )
        return raw_concept.description
