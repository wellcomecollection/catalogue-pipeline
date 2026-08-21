from pydantic import BaseModel

from ingestor.models.neptune.node import WorkNode
from models.pipeline.work_state import WorkAncestor

from .availability import is_available_online


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    totalParts: int
    isAvailableOnline: bool | None = None
    type: str = "Work"

    @staticmethod
    def from_neptune_node(node: WorkNode, total_parts: int) -> "DisplayRelation":
        return DisplayRelation(
            id=node.properties.id,
            title=node.properties.label,
            type=node.properties.type,
            referenceNumber=node.properties.collection_path_label,
            totalParts=total_parts,
            isAvailableOnline=is_available_online(node.properties.availabilities),
        )

    @staticmethod
    def from_work_ancestor(ancestor: WorkAncestor) -> "DisplayRelation":
        # Used for 'Series' relationships
        return DisplayRelation(
            id=None,
            title=ancestor.title,
            type=ancestor.work_type,
            totalParts=ancestor.num_children,
            # A series is not a work in its own right, so it has no online availability to report
            # `isAvailableOnline` is left unset (and so omitted from the document) rather than set to `false`,
            # which would imply the series is known not to be available online.
        )
