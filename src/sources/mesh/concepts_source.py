import xml.etree.ElementTree as ET
from collections.abc import Generator

import requests

from sources.base_source import BaseSource


class MeSHConceptsSource(BaseSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[ET.Element]:
        response = requests.get(self.url, stream=True)
        response.raw.decode_content = True

        events = ET.iterparse(response.raw)
        return (elem for _, elem in events if elem.tag == "DescriptorRecord")
