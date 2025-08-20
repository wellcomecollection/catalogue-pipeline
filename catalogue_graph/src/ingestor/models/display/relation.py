from typing import Self

from pydantic import BaseModel

from ingestor.models.neptune.node import WorkNode


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    partOf: list[Self] | None = None
    totalParts: int
    type: str = "Work"

    @staticmethod
    def from_neptune_node(node: WorkNode, total_parts: int) -> "DisplayRelation":
        return DisplayRelation(
            id=node.properties.id,
            title=node.properties.label,
            type=node.properties.type,
            referenceNumber=node.properties.reference_number,
            totalParts=total_parts,
        )

    @staticmethod
    def from_flat_hierarchy(
        hierarchy: list[WorkNode], total_parts: int
    ) -> "DisplayRelation":
        """Recursively build a hierarchy of ancestors from a flat list, starting from the parent (the first item
        in the list) to the root ancestor work (the last item in the list)."""
        relation = DisplayRelation.from_neptune_node(hierarchy[0], total_parts)
        if len(hierarchy) > 1:
            relation.partOf = [DisplayRelation.from_flat_hierarchy(hierarchy[1:], 1)]

        return relation
