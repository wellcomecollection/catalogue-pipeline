import pyarrow as pa
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.schema import Schema
from pyiceberg.types import BooleanType, NestedField, StringType, TimestamptzType

# Define matching Iceberg and Arrow schemas for the adapter store
ADAPTER_STORE_ICEBERG_SCHEMA = Schema(
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
        field_id=5, name="last_modified", field_type=TimestamptzType(), required=True
    ),
    # Whether the record has been deleted.
    NestedField(
        field_id=6,
        name="deleted",
        field_type=BooleanType(),
        required=False,
        default_value=False,
    ),
)
ADAPTER_STORE_ARROW_SCHEMA: pa.Schema = schema_to_pyarrow(ADAPTER_STORE_ICEBERG_SCHEMA)

RECONCILER_STORE_ICEBERG_SCHEMA = Schema(
    NestedField(field_id=1, name="namespace", field_type=StringType(), required=True),
    NestedField(field_id=2, name="id", field_type=StringType(), required=True),
    NestedField(field_id=3, name="guid", field_type=StringType(), required=True),
    NestedField(field_id=4, name="changeset", field_type=StringType(), required=False),
    NestedField(
        field_id=5, name="last_modified", field_type=TimestamptzType(), required=True
    ),
)
RECONCILER_STORE_ARROW_SCHEMA: pa.Schema = schema_to_pyarrow(
    RECONCILER_STORE_ICEBERG_SCHEMA
)
