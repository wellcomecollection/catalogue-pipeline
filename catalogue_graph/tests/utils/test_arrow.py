import typing

import pyarrow as pa
import pytest
from pydantic import BaseModel, Field

from utils.arrow import (
    _merge_structs,
    pydantic_to_pyarrow_schema,
    python_type_to_pyarrow,
)


def _struct_to_dict(struct: pa.StructType) -> dict:
    return {f.name: f.type for f in struct}


def _get_struct_fields(struct: pa.DataType) -> dict:
    """Given a PyArrow struct, return a dict of `field_name -> pa.DataType`"""
    if not isinstance(struct, pa.StructType):
        raise TypeError("Expected a pyarrow StructType")

    return {fld.name: fld.type for fld in struct}


def test_primitive_types() -> None:
    assert python_type_to_pyarrow(None) == pa.null()
    assert python_type_to_pyarrow(int) == pa.int64()
    assert python_type_to_pyarrow(str) == pa.string()


def test_list_type() -> None:
    result = python_type_to_pyarrow(list[int])
    assert isinstance(result, pa.ListType)
    # mypy narrows after isinstance check
    assert result.value_type == pa.int64()

    # Nested lists should be converted recursively
    nested_result = python_type_to_pyarrow(list[list[str]])
    assert isinstance(nested_result, pa.ListType)
    outer = nested_result
    assert isinstance(outer.value_type, pa.ListType)
    inner = outer.value_type
    assert inner.value_type == pa.string()


def test_resolve_optional_type() -> None:
    # `int | None` should collapse to int64
    assert python_type_to_pyarrow(int | None) == pa.int64()


def test_merge_structs() -> None:
    s1 = pa.struct([pa.field("a", pa.int64()), pa.field("b", pa.string())])
    s2 = pa.struct([pa.field("c", pa.bool_())])
    merged = _merge_structs([s1, s2])
    assert _get_struct_fields(merged) == {
        "a": pa.int64(),
        "b": pa.string(),
        "c": pa.bool_(),
    }


def test_cannot_merge_structs() -> None:
    s1 = pa.struct([pa.field("x", pa.int64())])
    s2 = pa.struct([pa.field("x", pa.string())])
    with pytest.raises(ValueError):
        _merge_structs([s1, s2])


def test_resolve_union_types() -> None:
    class ModelA(BaseModel):
        c1: int
        shared: str

    class ModelB(BaseModel):
        d1: bool
        shared: str

    result = python_type_to_pyarrow(ModelA | ModelB)
    assert _get_struct_fields(result) == {
        "c1": pa.int64(),
        "d1": pa.bool_(),
        "shared": pa.string(),
    }


def test_cannot_resolve_union_types() -> None:
    class ModelA(BaseModel):
        alpha: int
        shared: int

    class ModelB(BaseModel):
        beta: str
        shared: str  # Conflict on 'shared' (int vs str)

    with pytest.raises(ValueError):
        python_type_to_pyarrow(ModelA | ModelB)


def test_do_not_use_serialisation_alias() -> None:
    class SomeModel(BaseModel):
        one: int
        two: list[str] = Field(serialization_alias="renamed_two")

    struct_schema = pydantic_to_pyarrow_schema([SomeModel])
    schema = _struct_to_dict(struct_schema)
    assert "one" in schema and "two" in schema
    assert schema["one"] == pa.int64()
    # Field is a list of strings -> ListType whose value_type is string
    assert isinstance(schema["two"], pa.ListType)
    assert schema["two"].value_type == pa.string()


def test_nested_pydantic_models() -> None:
    class NestedModel(BaseModel):
        two: int

    class SomeModel(BaseModel):
        one: list[NestedModel]

    struct_schema = pydantic_to_pyarrow_schema([SomeModel])
    schema = _struct_to_dict(struct_schema)
    assert isinstance(schema["one"], pa.ListType)
    list_type = schema["one"]
    assert isinstance(list_type.value_type, pa.StructType)

    fields = _get_struct_fields(list_type.value_type)
    assert fields == {"two": pa.int64()}


def test_python_type_to_pyarrow_unsupported_type_raises() -> None:
    class SomethingElse:
        pass

    with pytest.raises(TypeError):
        python_type_to_pyarrow(SomethingElse)


def test_resolve_literal() -> None:
    lit = typing.Literal[1, 2, None]
    result = python_type_to_pyarrow(lit)
    assert result == pa.int64()


def test_cannot_resolve_literal() -> None:
    lit2 = typing.Literal[1, "a"]
    with pytest.raises(ValueError):
        python_type_to_pyarrow(lit2)
