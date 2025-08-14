from typing import Self

from pydantic import BaseModel

from ingestor.models.neptune.node import WorkNode


class DisplayRelation(BaseModel):
    id: str | None
    title: str | None
    referenceNumber: str | None = None
    partOf: list[Self] | None = None
    totalParts: int
    # totalDescendentParts: int # TODO: Is this field needed?
    type: str = "Work"

    @staticmethod
    def from_neptune_node(node: WorkNode, total_parts: int) -> "DisplayRelation":
        return DisplayRelation(
            id=node.properties.id,
            title=node.properties.label,
            type=node.properties.type,
            referenceNumber="",  # TODO: Add reference number
            totalParts=total_parts,
        )

    @staticmethod
    def from_flat_hierarchy(
        hierarchy: list[WorkNode], total_parts: int
    ) -> "DisplayRelation":
        relation = DisplayRelation.from_neptune_node(hierarchy[0], total_parts)
        if len(hierarchy) > 1:
            relation.partOf = [DisplayRelation.from_flat_hierarchy(hierarchy[1:], 1)]

        return relation
