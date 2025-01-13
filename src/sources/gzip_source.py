import gzip
import json
from collections.abc import Generator

import requests

from .base_source import JSONSource


class GZipSource(JSONSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[dict]:
        response = requests.get(self.url, stream=True)

        with gzip.GzipFile(fileobj=response.raw) as file:
            for line_bytes in file:
                yield json.loads(line_bytes.decode("utf8"))


class MultiGZipSource(JSONSource):
    def __init__(self, urls: list[str]):
        self.urls = urls

    def stream_raw(self) -> Generator[dict]:
        for url in self.urls:
            source = GZipSource(url)
            yield from source.stream_raw()
