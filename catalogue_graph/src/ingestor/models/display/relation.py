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
            referenceNumber="", # TODO: Add reference number
            totalParts=total_parts,
        )

    @staticmethod
    def from_flat_hierarchy(hierarchy: list[WorkNode]) -> "DisplayRelation":
        current_work = hierarchy[0]
        if len(hierarchy) == 1:
            return DisplayRelation.from_neptune_node(current_work, 1)
            
        return DisplayRelation(
            id=current_work.properties.id,
            title=current_work.properties.label,
            referenceNumber="", # TODO: Add reference number
            totalParts=0,
            partOf=[DisplayRelation.from_flat_hierarchy(hierarchy[1:])]
        )    
