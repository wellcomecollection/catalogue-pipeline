import xml.etree.ElementTree as ET
from typing import Any, Literal

import requests

ID_PREFIX = "http://id.nlm.nih.gov/mesh/"


class RawMeSHConcept:
    def __init__(self, raw_concept: ET.Element):
        self.raw_concept = raw_concept
        self.source: Literal["nlm-mesh"] = "nlm-mesh"

    @staticmethod
    def _remove_id_prefix(raw_id: str) -> str:
        """Removes prefix from MeSH descriptor (only present in extra JSON)."""
        return raw_id.removeprefix(ID_PREFIX)

    @property
    def source_id(self) -> str:
        """Returns MeSH descriptor (unique ID)."""
        desc_elem = self.raw_concept.find("DescriptorUI")

        assert isinstance(desc_elem, ET.Element)
        descriptor = desc_elem.text
        assert isinstance(descriptor, str)

        return descriptor

    @property
    def label(self) -> str:
        """Returns the concept label."""
        label_elem = self.raw_concept.find("DescriptorName//String")

        assert isinstance(label_elem, ET.Element)
        label = label_elem.text
        assert isinstance(label, str)

        return label

    @property
    def alternative_labels(self) -> list[str]:
        """Returns a list of alternative labels for the concept."""
        altern_labels = []

        for altern_concept in self.raw_concept.findall(
            "ConceptList//Concept[@PreferredConceptYN='N']"
        ):
            altern_label_elem = altern_concept.find("ConceptName//String")
            if isinstance(altern_label_elem, ET.Element):
                altern_label = altern_label_elem.text
                assert isinstance(altern_label, str)
                altern_labels.append(altern_label)

        return altern_labels

    @property
    def alternative_ids(self) -> list[str]:
        """Returns a list of MeSH tree numbers for the concept."""
        treenums = []
        for treenum_elem in self.raw_concept.findall("TreeNumberList//TreeNumber"):
            if isinstance(treenum_elem, ET.Element):
                treenum = treenum_elem.text
                assert isinstance(treenum, str)
                treenums.append(treenum)

        return treenums

    @property
    def description(self) -> str | None:
        """Returns the preferred term's scope note (free-text narrative of its scope and meaning)."""
        scope_note = None

        scope_note_elem = self.raw_concept.find(
            "ConceptList//Concept[@PreferredConceptYN='Y']//ScopeNote"
        )
        if isinstance(scope_note_elem, ET.Element):
            scope_note = scope_note_elem.text

        return scope_note

    @staticmethod
    def fetch_mesh(source_id: str) -> Any:
        """Fetches JSON containing RDF data for a given MeSH concept."""

        response = requests.get(f"https://id.nlm.nih.gov/mesh/{source_id}.json")
        return response.json()

    @property
    def parent_concept_ids(self) -> list[str]:
        """Extracts parent MeSH descriptors from JSON."""

        mesh_data = self.fetch_mesh(self.source_id)
        broader_desc = mesh_data.get("broaderDescriptor", [])

        if not isinstance(broader_desc, list):
            broader_desc = [broader_desc]

        return [self._remove_id_prefix(desc) for desc in broader_desc]

    @property
    def related_concept_ids(self) -> list[str]:
        """Extracts related MeSH descriptors."""

        related_descriptors = []
        for desc_elem in self.raw_concept.findall(
            "SeeRelatedDescriptor//DescriptorReferredTo//DescriptorUI"
        ):
            if isinstance(desc_elem, ET.Element):
                desc = desc_elem.text
                assert isinstance(desc, str)
                related_descriptors.append(desc)

        return related_descriptors

    @property
    def is_geographic(self) -> bool:
        """Returns True if the node represents a geographic concept, as determined by `DescriptorClass`."""

        return self.raw_concept.attrib.get("DescriptorClass") == "4"
