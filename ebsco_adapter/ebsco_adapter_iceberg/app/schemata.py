from pyiceberg.schema import Schema
from pyiceberg.types import NestedField, IntegerType, StringType
import pyarrow as pa

SCHEMA = Schema(
    NestedField(field_id=1, name='id', field_type=StringType(), required=True),
    NestedField(field_id=3, name='content', field_type=StringType(), required=False),
    NestedField(field_id=2, name='changeset', field_type=StringType(), required=False)
)

ARROW_SCHEMA = pa.schema(
    [
        pa.field("id", type=pa.string(), nullable=False),
        pa.field("content", type=pa.string(), nullable=True)
    ]
)
