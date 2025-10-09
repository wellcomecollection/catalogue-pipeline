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


def _dict_to_struct(field_map: dict) -> pa.StructType:
    fields = [pa.field(name, dtype) for name, dtype in field_map.items()]
    return pa.struct(fields)


def _merge_structs(structs: list[pa.StructType]) -> pa.StructType:
    """Merge a union of structs into a single struct containing a union of all of their fields"""
    merged_struct: dict[str, pa.DataType] = {}

    for struct in structs:
        for field in struct:
            existing, new = merged_struct.get(field.name), field.type

            if existing is None:
                merged_struct[field.name] = new
            elif isinstance(existing, pa.StructType) and isinstance(new, pa.StructType):
                merged_struct[field.name] = _merge_structs([existing, new])
            elif existing != new:
                # If field types conflict, we cannot produce a single merged struct
                raise ValueError(
                    f"The following structs cannot be merged into a single PyArrow type: {structs}."
                )

    return _dict_to_struct(merged_struct)


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
        return pydantic_to_pyarrow_schema([annotation])

    raise TypeError(
        f"Converting type '{annotation}' to a PyArrow type is not supported.'"
        f"Use a different type or add support for type '{annotation}' above."
    )


def pydantic_to_pyarrow_schema(
    model_classes: list[type[BaseModel]],
) -> pa.StructType:
    """Create a pyarrow schema compatible with all specified Pydantic model classes."""

    if len(model_classes) > 1:
        structs = [pydantic_to_pyarrow_schema([m]) for m in model_classes]
        return _merge_structs(structs)

    schema_map = {}
    for field_name, field in model_classes[0].model_fields.items():
        annotation = python_type_to_pyarrow(field.annotation)
        schema_map[field_name] = annotation

    return _dict_to_struct(schema_map)
