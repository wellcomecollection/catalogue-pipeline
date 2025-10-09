from pydantic import BaseModel

from ingestor.models.merged.work import WorkAncestor
from ingestor.models.neptune.node import WorkNode


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    totalParts: int
    type: str = "Work"

    @staticmethod
    def from_neptune_node(node: WorkNode, total_parts: int) -> "DisplayRelation":
        return DisplayRelation(
            id=node.properties.id,
            title=node.properties.label,
            type=node.properties.type,
            referenceNumber=node.properties.collection_path_label,
            totalParts=total_parts,
        )

    @staticmethod
    def from_work_ancestor(ancestor: WorkAncestor) -> "DisplayRelation":
        # Used for 'Series' relationships
        return DisplayRelation(
            id=None,
            title=ancestor.title,
            type=ancestor.work_type,
            totalParts=ancestor.num_children,
        )
