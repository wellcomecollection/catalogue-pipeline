import csv
import os
from collections.abc import Generator
from contextlib import contextmanager
from typing import TextIO

from sources.base_source import BaseSource

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_PATH = os.path.join(HERE, "wellcome_collection_authority.csv")


class WeCoConceptsSource(BaseSource):
    def __init__(self, source_csv: TextIO | None = None):
        self.source_csv = source_csv

    @contextmanager
    def _open_reader(self) -> Generator[csv.DictReader]:
        if self.source_csv is None:
            with open(DEFAULT_PATH) as f:
                yield csv.DictReader(f)
        else:
            yield csv.DictReader(self.source_csv)

    def stream_raw(self) -> Generator[dict[str, str]]:
        """Streams raw WECO concept records."""
        with self._open_reader() as reader:
            yield from reader
