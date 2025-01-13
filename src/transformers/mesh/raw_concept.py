import requests
import xml.etree.ElementTree as ET

ID_PREFIX = "http://id.nlm.nih.gov/mesh/"


class RawMeSHConcept:
    def __init__(self, raw_concept: ET.Element):
        self.raw_concept = raw_concept
        self.source = "mesh"

    @staticmethod
    def _remove_id_prefix(raw_id: str) -> str:
        """Removes prefix from MeSH descriptor (only present in JSON)."""

        return raw_id.removeprefix(ID_PREFIX)

    @property
    def source_id(self) -> str:
        """Returns MeSH descriptor (unique ID)."""

        return self.raw_concept.find("DescriptorUI").text

    @property
    def label(self) -> str:
        """Returns the concept label."""

        return self.raw_concept.find('DescriptorName//String').text

    @property
    def alternative_labels(self) -> list[str] | None:
        """Returns a list of alternative labels for the concept."""
        altern_labels = []

        for altern_concept in self.raw_concept.findall("ConceptList//Concept[@PreferredConceptYN='N']"):
            altern_labels.append(altern_concept.find("ConceptName//String").text)
        
        return altern_labels

    @property
    def alternative_ids(self) -> list[str]:
        """Returns a list of MeSH tree numbers for the concept."""

        return [treenum.text for treenum in self.raw_concept.findall("TreeNumberList//TreeNumber")]

    @property
    def description(self) -> str | None:
        """Returns the preferred term's scope note (free-text narrative of its scope and meaning)."""
        
        scope_note = self.raw_concept.find("ConceptList//Concept[@PreferredConceptYN='Y']//ScopeNote")
        if scope_note is not None:
            return scope_note.text
        
        return scope_note

    @staticmethod
    def fetch_mesh(source_id: str) -> dict:
        """Fetch JSON containing RDF data for a given MeSH concept."""

        response = requests.get(f"https://id.nlm.nih.gov/mesh/{source_id}.json")
        return response.json()

    @property
    def parent_concept_ids(self) -> list[str]:
        """Extract parent MeSH descriptors from JSON."""

        mesh_data = self.fetch_mesh(self.source_id)
        broader_desc = mesh_data.get("broaderDescriptor", [])

        if not isinstance(broader_desc, list):
            broader_desc = [broader_desc]
        
        return [self._remove_id_prefix(desc) for desc in broader_desc]
    
    @property
    def related_concept_ids(self) -> list[str]:
        """Extract related MeSH descriptors."""

        related_desc = self.raw_concept.findall("SeeRelatedDescriptor")

        return [desc.find("DescriptorReferredTo//DescriptorUI").text for desc in related_desc]

    @property
    def is_geographic(self) -> bool:
        """Returns True if the node represents a geographic concept, as determined by `DescriptorClass`."""
        
        return self.raw_concept.attrib["DescriptorClass"] == "4"
