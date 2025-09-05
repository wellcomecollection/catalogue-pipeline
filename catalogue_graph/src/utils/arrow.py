import datetime
import types
import typing
from typing import get_args, get_origin

import pyarrow as pa
from pydantic import BaseModel

FIELD_MAP: dict[type, pa.DataType] = {
    int: pa.int64(),
    str: pa.string(),
    bytes: pa.binary(),
    bool: pa.bool_(),
    float: pa.float64(),
    datetime.date: pa.date32(),
    datetime.time: pa.time64("us"),
    datetime.datetime: pa.timestamp("us"),
}


def _merge_structs(structs: list[pa.StructType]) -> pa.DataType:
    """Merge a union of structs into a single struct containing a union of all of their fields"""
    merged_struct = {}

    for struct in structs:
        for field in struct:
            if field.name not in merged_struct:
                merged_struct[field.name] = field.type
            elif merged_struct[field.name] != field.type:
                # If field types conflict, we cannot produce a single merged struct
                raise ValueError(
                    f"The following structs cannot be merged into a single PyArrow type: {structs}."
                )

    fields = [pa.field(name, dtype) for name, dtype in merged_struct.items()]
    return pa.struct(fields)


def _resolve_union_types(annotations: typing.Iterable[type]) -> pa.DataType:
    union_types = []
    for annotation in annotations:
        if annotation is not type(None):
            union_types.append(python_type_to_pyarrow(annotation))

    if len(set(union_types)) == 1:
        return union_types[0]

    # Try to create a single struct from a union of structs
    structs = [u for u in union_types if isinstance(u, pa.StructType)]
    if len(structs) == len(union_types):
        return _merge_structs(structs)

    raise ValueError(
        f"The following annotations cannot be merged into a single PyArrow type: {annotations}"
    )


def python_type_to_pyarrow(annotation: typing.Any) -> pa.DataType:
    """Convert a Python type annotation into a PyArrow type annotation (e.g. `int` -> `pa.int64()`)."""

    # Convert common primitives into PyArrow type annotations
    if annotation is None:
        return pa.null()
    if annotation in FIELD_MAP:
        return FIELD_MAP[annotation]

    origin = get_origin(annotation)
    args = get_args(annotation)

    if origin is typing.Literal:
        return _resolve_union_types([type(arg) for arg in args])
    if origin in (types.UnionType, typing.Union):
        return _resolve_union_types(args)
    if origin is list:
        return pa.list_(python_type_to_pyarrow(args[0]))

    if issubclass(annotation, BaseModel):
        schema_map = pydantic_to_pyarrow_schema(annotation)
        fields = [pa.field(name, dtype) for name, dtype in schema_map.items()]
        return pa.struct(fields)

    raise TypeError(
        f"Converting type '{annotation}' to a PyArrow type is not supported.'"
        f"Use a different type or add support for type '{annotation}' above."
    )


def pydantic_to_pyarrow_schema(model_class: type[BaseModel]) -> dict[str, pa.DataType]:
    """Convert a Pydantic model into a pyarrow schema"""
    schema = {}
    for field_name, field in model_class.model_fields.items():
        schema[field_name] = python_type_to_pyarrow(field.annotation)

    return schema
