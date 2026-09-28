import json
from datetime import datetime

from pyiceberg.schema import Schema
from pyiceberg.table.sorting import SortField, SortOrder
from pyiceberg.transforms import IdentityTransform
from pyiceberg.types import (
    IntegerType,
    ListType,
    NestedField,
    StringType,
    TimestamptzType,
)

# One row per identified work, written by the id minter. The graph columns are
# promoted out of `content` so the matcher can read them without parsing documents.
WORKS_IDENTIFIED_ICEBERG_SCHEMA = Schema(
    NestedField(field_id=1, name="id", field_type=StringType(), required=True),
    NestedField(field_id=2, name="version", field_type=IntegerType(), required=True),
    NestedField(field_id=3, name="type", field_type=StringType(), required=True),
    NestedField(
        field_id=4,
        name="source_identifier_type",
        field_type=StringType(),
        required=True,
    ),
    NestedField(
        field_id=5,
        name="source_identifier_value",
        field_type=StringType(),
        required=True,
    ),
    NestedField(
        field_id=6,
        name="merge_candidate_ids",
        field_type=ListType(
            element_id=7, element_type=StringType(), element_required=True
        ),
        required=True,
    ),
    NestedField(field_id=8, name="content", field_type=StringType(), required=True),
    NestedField(
        field_id=9, name="last_modified", field_type=TimestamptzType(), required=True
    ),
)

WORKS_IDENTIFIED_SORT_ORDER = SortOrder(
    SortField(source_id=1, transform=IdentityTransform())
)


def works_identified_row(document: dict, last_modified: datetime) -> dict:
    """A table row for one identified work document."""
    state = document["state"]
    return {
        "id": state["canonicalId"],
        "version": document["version"],
        "type": document["type"],
        "source_identifier_type": state["sourceIdentifier"]["identifierType"]["id"],
        "source_identifier_value": state["sourceIdentifier"]["value"],
        "merge_candidate_ids": [
            candidate["id"]["canonicalId"]
            for candidate in state.get("mergeCandidates", [])
        ],
        "content": json.dumps(document),
        "last_modified": last_modified,
    }
