from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, IntegerType, StringType, TimestamptzType
import pyarrow as pa

# namespace - e.g. ebsco - in case we decide to store everything in one table
# last modified - different from changeset because this allows changeset to be meaningful.

SCHEMA = Schema(
    NestedField(field_id=1, name="namespace", field_type=StringType(), required=True),
    NestedField(field_id=2, name="id", field_type=StringType(), required=True),
    NestedField(field_id=3, name="content", field_type=StringType(), required=False),
    NestedField(field_id=4, name="changeset", field_type=StringType(), required=False),
    NestedField(
        field_id=5, name="last_modified", field_type=TimestamptzType(), required=False
    ),
)

# The Arrow schema corresponds to the "Real Data" as stored in iceberg, i.e. without
# the metadata recording when it was changed.
# This is used to compare new data with existing data in order to apply updates.
ARROW_SCHEMA = pa.schema(
    [
        pa.field("namespace", type=pa.string(), nullable=False),
        pa.field("id", type=pa.string(), nullable=False),
        pa.field("content", type=pa.string(), nullable=True),
    ]
)
