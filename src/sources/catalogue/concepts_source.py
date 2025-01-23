import gzip
import json
from collections.abc import Generator

import requests

from sources.base_source import BaseSource

CONCEPT_KEYS = ["subjects", "genres", "contributors"]

class CatalogueConceptSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[dict]:
        response = requests.get(self.url, stream=True)

        with gzip.GzipFile(fileobj=response.raw) as file:
            for line_bytes in file:
                work = json.loads(line_bytes.decode("utf8"))
                for conecpt_key in CONCEPT_KEYS:
                    for raw_concept in work.get(conecpt_key, []):
                        yield raw_concept
