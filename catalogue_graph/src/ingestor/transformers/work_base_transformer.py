from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.transformers.raw_concept import (
    DISPLAY_SOURCE_PRIORITY,
    get_priority_label,
)
from models.pipeline.concept import Concept


class WorkBaseTransformer:
    def __init__(self, extracted: VisibleExtractedWork):
        self.neptune_concepts = {c.concept.id: c for c in extracted.concepts}
        self.data = extracted.work.data
        self.state = extracted.work.state
        self.hierarchy = extracted.hierarchy

    def get_standard_concept_label(self, concept: Concept) -> str:
        """Return the highest priority label for the given concept, as determined by the catalogue graph."""
        standard_label = concept.label
        if concept.id.canonical_id in self.neptune_concepts:
            extracted = self.neptune_concepts[concept.id.canonical_id]
            standard_label, _ = get_priority_label(extracted, DISPLAY_SOURCE_PRIORITY)

        return standard_label

    @property
    def collection_path_path(self) -> str | None:
        """The work's full collection path.

        Some works (e.g. works in the Fallaize Collection) store incomplete collection paths which only consist
        of <parent ID>/<work ID>. We want to use the full collection path, so we construct it here using
        ancestors paths. For example, given the collection path 'C/D' and ancestors collections paths
        'B/C', 'A/B', and 'A', return 'A/B/C/D'.

        A small number of works have a trailing slash in their collection path, which is removed to
        match how path identifiers are extracted when building the graph.
        """
        if self.data.collection_path is None:
            return None

        path_fragments = self.data.collection_path.path.rstrip("/").split("/")
        for a in self.hierarchy.ancestors:
            if ancestor_path := a.work.properties.collection_path:
                ancestor_path_fragments = ancestor_path.rstrip("/").split("/")
                if ancestor_path_fragments[-1] == path_fragments[0]:
                    path_fragments = ancestor_path_fragments[:-1] + path_fragments

        return "/".join(path_fragments)

    @property
    def is_collection_root(self) -> bool:
        """Returns true if the work is at the top of a collection hierarchy.

        Collection hierarchies come from collection paths, so this includes hierarchies
        which are not archives (such as those derived from Sierra 773/774 fields).

        A work is at the top of its hierarchy when its collection path consists of a
        single segment. This is determined from the path alone rather than from the
        graph, since some collection roots have no children in the public catalogue,
        and works can become detached from their ancestors when hierarchical data is
        incomplete.
        """
        path = self.collection_path_path
        if path is None:
            return False

        return len(path) > 0 and "/" not in path
