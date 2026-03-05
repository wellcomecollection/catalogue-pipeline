import gzip
import json
from collections.abc import Generator
from typing import Any

import requests


class BaseSource:
    def stream_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to an entity extracted from the source."""
        raise NotImplementedError("Each source must implement a `stream_raw` method.")


class GZipSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[dict]:
        response = requests.get(self.url, stream=True)

        with gzip.GzipFile(fileobj=response.raw) as file:
            for line_bytes in file:
                yield json.loads(line_bytes.decode("utf8"))


class MultiGZipSource(BaseSource):
    def __init__(self, urls: list[str]):
        self.urls = urls

    def stream_raw(self) -> Generator[dict]:
        for url in self.urls:
            source = GZipSource(url)
            yield from source.stream_raw()
