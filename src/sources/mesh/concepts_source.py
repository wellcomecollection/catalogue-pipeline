from collections.abc import Generator
import requests
import xml.etree.ElementTree as ET

from sources.base_source import XMLSource

class MeSHConceptsSource(XMLSource):
    def __init__(self, url: str):
        self.url = url

    def stream_raw(self) -> Generator[ET.Element]:
        response = requests.get(self.url, stream=True)
        response.raw.decode_content = True

        events = ET.iterparse(response.raw)
        return (
            elem for _, elem in events if elem.tag == "DescriptorRecord"
        )
