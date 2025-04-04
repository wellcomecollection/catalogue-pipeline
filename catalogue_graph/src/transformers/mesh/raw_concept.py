import xml.etree.ElementTree as ET
from typing import Literal

from sources.mesh.concepts_source import RawMeshNode
from utils.xml import assert_get_text


class RawMeSHConcept:
    def __init__(self, raw_concept: RawMeshNode):
        self.raw_concept = raw_concept[0]
        self.treenum_lookup = raw_concept[1]
        self.source: Literal["nlm-mesh"] = "nlm-mesh"

    @property
    def source_id(self) -> str:
        """Returns MeSH descriptor (unique ID)."""
        descriptor_elem = self.raw_concept.find("DescriptorUI")
        source_id = assert_get_text(descriptor_elem)

        return source_id

    @property
    def label(self) -> str:
        """Returns the concept label."""
        label_elem = self.raw_concept.find("DescriptorName//String")
        label = assert_get_text(label_elem)

        return label

    @property
    def alternative_labels(self) -> list[str]:
        """Returns a list of alternative labels for the concept, if available."""
        altern_labels = [
            assert_get_text(altern_label)
            for altern_label in self.raw_concept.findall(
                "ConceptList//Concept//TermList//Term//String"
            )
        ]
        altern_labels.remove(self.label)

        return altern_labels

    @property
    def alternative_ids(self) -> list[str]:
        """Returns a list of MeSH tree numbers for the concept."""
        treenums = []

        for treenum_elem in self.raw_concept.findall("TreeNumberList//TreeNumber"):
            treenums.append(assert_get_text(treenum_elem))

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
    def _get_parent_treenum(treenum: str) -> str:
        """
        Extracts the parent tree number by removing all digits after
        the child tree number's last "."
        """
        parent_treenum = treenum.split(".")[:-1]
        return ".".join(parent_treenum)

    @property
    def parent_concept_ids(self) -> list[str]:
        """
        Extracts parent MeSH descriptors from the tree number lookup.
        This is possible because each concept's MeSH tree number encodes
        its hierarchical relationships, e.g.: The parent tree number
        of a MeSH term with tree number "A10.690.552.500" is "A10.690.552"
        """
        parent_source_ids = set()

        for treenum in self.alternative_ids:
            # Make sure the child tree number is not at the top level of the hierarchy
            if "." in treenum:
                parent_treenum = self._get_parent_treenum(treenum)
                parent_source_id = self.treenum_lookup[parent_treenum]
                parent_source_ids.add(parent_source_id)

        return list(parent_source_ids)

    @property
    def related_concept_ids(self) -> list[str]:
        """Extracts related MeSH descriptors."""

        related_descriptors = []
        for desc_elem in self.raw_concept.findall(
            "SeeRelatedList//SeeRelatedDescriptor//DescriptorReferredTo//DescriptorUI"
        ):
            related_descriptors.append(assert_get_text(desc_elem))

        return related_descriptors

    @property
    def is_geographic(self) -> bool:
        """Returns True if the node represents a geographic concept, as determined by `DescriptorClass`."""

        return self.raw_concept.attrib.get("DescriptorClass") == "4"
