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
