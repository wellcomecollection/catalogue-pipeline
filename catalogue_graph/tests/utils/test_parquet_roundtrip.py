import tempfile
from pathlib import Path

import polars as pl
import pyarrow as pa
import pyarrow.parquet as pq
from pyarrow import types as pa_types
from pydantic import BaseModel

from utils.arrow import pydantic_to_pyarrow_schema


class NestedModel(BaseModel):
    value: float


class SampleModel(BaseModel):
    int_field: int
    str_field: str
    optional_field: int | None
    list_field: list[str]
    nested: list[NestedModel]


def _arrow_schema_for(model: type[BaseModel]) -> pa.Schema:
    return pa.schema(pydantic_to_pyarrow_schema([model]))


def _is_pa_string(t: pa.DataType) -> bool:
    return pa_types.is_string(t) or pa_types.is_large_string(t)


def _is_pa_list(t: pa.DataType) -> bool:
    return pa_types.is_list(t) or pa_types.is_large_list(t)


def _are_pa_types_equivalent(a: pa.DataType, b: pa.DataType) -> bool:
    """Return True if the two Arrow types are considered equivalent for our purposes.

    We tolerate widening in these cases:
    - string -> large_string
    - list<string> -> large_list<large_string>
    - list<T> -> large_list<T> (same element type per this function recursively)
    """
    # Direct equality fast path
    if a == b:
        return True

    # Plain/large string equivalence
    if _is_pa_string(a) and _is_pa_string(b):
        return True

    # List vs large_list equivalence (including element widening)
    if _is_pa_list(a) and _is_pa_list(b):
        elem_a = a.value_type  # type: ignore[attr-defined]
        elem_b = b.value_type  # type: ignore[attr-defined]
        return _are_pa_types_equivalent(elem_a, elem_b)

    return False


def test_schema_stability_and_round_trip() -> None:
    # Build expected schema twice to ensure stability (no mutation/order changes)
    schema_first = _arrow_schema_for(SampleModel)
    schema_second = _arrow_schema_for(SampleModel)
    assert schema_first == schema_second

    # Create some sample rows including a None to exercise optional handling
    rows = [
        SampleModel(
            int_field=1,
            str_field="alpha",
            optional_field=10,
            list_field=["a", "b"],
            nested=[NestedModel(value=1.5), NestedModel(value=2.5)],
        ),
        SampleModel(
            int_field=2,
            str_field="beta",
            optional_field=None,  # ensure optional still results in same arrow dtype
            list_field=["c"],
            nested=[NestedModel(value=3.14)],
        ),
    ]

    table = pa.Table.from_pylist([r.model_dump() for r in rows], schema=schema_first)

    # Round-trip via parquet (mirrors production path: Arrow -> Polars -> Parquet -> Arrow)
    with tempfile.TemporaryDirectory() as tmpdir:
        out_path = Path(tmpdir) / "sample.parquet"
        pl.DataFrame(table).write_parquet(out_path)
        read_back = pq.read_table(out_path)

    # Compare schemas (field order, names, types). Nullability may differ if Polars adjusted;
    # enforce type equality per field.
    assert len(schema_first) == len(read_back.schema)

    for expected_field, actual_field in zip(
        schema_first, read_back.schema, strict=False
    ):
        assert expected_field.name == actual_field.name
        if not _are_pa_types_equivalent(expected_field.type, actual_field.type):
            raise AssertionError(
                f"Type mismatch: {expected_field.type} vs {actual_field.type}"
            )

    # Spot-check data integrity of a nested list element and optional None preservation
    col_optional = read_back.column("optional_field")
    # Should contain at least one null (None) value
    assert col_optional.null_count > 0
    # Nested list column should be list type
    nested_field = read_back.schema.field("nested")
    # Accept either list or large_list after round trip
    assert _is_pa_list(nested_field.type)
    assert isinstance(nested_field.type.value_type, pa.StructType)
