
from pydantic import BaseModel

from ingestor.models.neptune.node import WorkNode


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    totalParts: int
    type: str = "Work"

    @classmethod
    def from_neptune_node(cls, node: WorkNode, total_parts: int) -> "DisplayRelation":
        return cls(
            id=node.properties.id,
            title=node.properties.label,
            type=node.properties.type,
            referenceNumber=node.properties.reference_number,
            totalParts=total_parts,
        )


class DisplayRelationWithAncestors(DisplayRelation):
    partOf: list["DisplayRelation"] = []
    
    @staticmethod
    def from_flat_hierarchy(
            hierarchy: list[WorkNode], total_parts: int
    ) -> "DisplayRelation":
        """Recursively build a hierarchy of ancestors from a flat list, starting from the parent (the first item
        in the list) to the root ancestor work (the last item in the list)."""
        relation = DisplayRelationWithAncestors.from_neptune_node(hierarchy[0], total_parts)
        relation.partOf = [DisplayRelation.from_neptune_node(a, 1) for a in hierarchy] 

        return relation
