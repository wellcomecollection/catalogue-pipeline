from sources.base_source import BaseSource
import csv
import os
from collections.abc import Generator
from typing import TextIO
from contextlib import contextmanager

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_PATH = os.path.join(HERE, "wellcome_collection_authority.csv")


def _load_concepts_from_csv(file_path: str) -> csv.DictReader:
    yield _load_concepts_from_textIO(f)


def _load_concepts_from_textIO(overrides: TextIO) -> csv.DictReader:
    return csv.DictReader(overrides)


class WeCoConceptsSource(BaseSource):
    def __init__(self, source_csv: TextIO | None = None):
        self.source_csv = source_csv

    @contextmanager
    def _open_reader(self):
        if self.source_csv is None:
            with open(DEFAULT_PATH) as f:
                yield csv.DictReader(f)
        else:
            yield csv.DictReader(self.source_csv)

    def stream_raw(self) -> Generator[dict[str, str]]:
        """Streams raw WECO concept records."""
        with self._open_reader() as reader:
            for row in reader:
                yield row
