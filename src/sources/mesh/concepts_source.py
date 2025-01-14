import xml.etree.ElementTree as ET
from collections.abc import Generator

import requests

from sources.base_source import BaseSource
from utils.xml import get_text


class MeSHConceptsSource(BaseSource):
    def __init__(self, url: str):
        self.mesh_data = self._get_mesh_data(url)
        self.treenum_lookup = self._treenum_lookup()

    @staticmethod
    def _get_mesh_data(url: str) -> ET.Element:
        response = requests.get(url)
        return ET.fromstring(response.content)

    def _treenum_lookup(self) -> dict[str, str]:
        """
        Creates lookup for MeSH tree numbers. This is needed to extract parent MeSH IDs, 
        which are not directly available in the XML DescriptorRecord.
        """
        treenum_lookup = {}
        for descriptor in self.mesh_data.findall("DescriptorRecord"):
            desc_ui = descriptor.find("DescriptorUI")
            for treenum in descriptor.findall("TreeNumberList//TreeNumber"):
                treenum_lookup[get_text(treenum)] = get_text(desc_ui)
        return treenum_lookup

    def stream_raw(self) -> Generator[tuple[ET.Element, dict[str,str]]]:
        
        for elem in self.mesh_data.iter():
            if elem.tag == "DescriptorRecord":
                yield elem, self.treenum_lookup
