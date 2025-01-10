import gzip
import json
from collections.abc import Generator
from typing import Literal, Union

import xml.etree.ElementTree as ET
import requests

from .base_source import BaseSource


class GZipSource(BaseSource):
    def __init__(self, url: str, ftype: Literal["json", "xml"] = "json"):
        self.url = url
        self.ftype = ftype

    def _stream_raw_json(self, response: requests.Response) -> Generator[dict]:
        with gzip.GzipFile(fileobj=response.raw) as file:
            for line_bytes in file:
                yield json.loads(line_bytes.decode("utf8"))
    
    def _stream_raw_xml(self, response: requests.Response) -> Generator[ET.Element]:
        response.raw.decode_content = True
        events = ET.iterparse(response.raw)
        for _, elem in events:
            yield elem

    def stream_raw(self) -> Generator[Union[dict, ET.Element]]:
        response = requests.get(self.url, stream=True)

        if self.ftype == "json":
            return self._stream_raw_json(response)
        
        elif self.ftype == "xml":
            return self._stream_raw_xml(response)
        
        else:
            raise ValueError("Unknown file type.")


class MultiGZipSource(BaseSource):
    def __init__(self, urls: list[str]):
        self.urls = urls

    def stream_raw(self) -> Generator[dict]:
        for url in self.urls:
            source = GZipSource(url)
            yield from source.stream_raw()
