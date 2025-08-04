from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, StringType, TimestamptzType
import pyarrow as pa

# namespace - e.g. ebsco - in case we decide to store everything in one table
# last modified - different from changeset because this allows changeset to be meaningful.

SCHEMA = Schema(
    # We may wish to store similar data from multiple sources in the same table. This allows us to filter appropriately
    NestedField(field_id=1, name="namespace", field_type=StringType(), required=True),
    # Each record has an identifier which is unique within its namespace
    NestedField(field_id=2, name="id", field_type=StringType(), required=True),
    # This is the body of the record - the thing we have extracted from the source
    # It is left otherwise unaltered by the adapter.
    # This could be anything, the updater does not care
    NestedField(field_id=3, name="content", field_type=StringType(), required=False),
    # An identifier for an "atomic" change.  This groups together all the update/insert/delete operations
    # associated with a single run of the adapter.
    NestedField(field_id=4, name="changeset", field_type=StringType(), required=False),
    # When a record was last modified.
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
