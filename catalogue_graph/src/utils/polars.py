import datetime
import types
import typing
from typing import get_args, get_origin

import polars as pl
from polars._typing import PolarsDataType
from pydantic import BaseModel


def _resolve_struct_union(structs: list[PolarsDataType]):
    merged_struct = {}

    for struct in structs:
        for field in struct.fields:
            if field.name not in merged_struct:
                merged_struct[field.name] = field.dtype
            elif merged_struct[field.name] != field.dtype:
                return pl.Object    
            
    return pl.Struct(merged_struct)


def _resolve_union_types(annotations: typing.Iterable[type]) -> PolarsDataType:
    union_types = []
    for annotation in annotations:
        if annotation is not type(None):
            union_types.append(python_type_to_polars(annotation))

    if len(set(union_types)) == 1:
        return union_types[0]
    
    if all([type(u) == pl.Struct for u in union_types]):
        return _resolve_struct_union(union_types)

    return pl.Object


def python_type_to_polars(
    annotation: type | None, parent_annotation: type | None = None
) -> PolarsDataType:
    """Convert a Python type annotation into a Polars type annotation."""
    origin = get_origin(annotation)
    args = get_args(annotation)

    # Convert common primitives into polars type annotations
    if annotation is None:
        return pl.Null
    if annotation is str:
        return pl.String
    if annotation is int:
        return pl.Int64
    if annotation is float:
        return pl.Float64
    if annotation is bool:
        return pl.Boolean
    if annotation is datetime.datetime:
        return pl.Datetime
    if annotation is datetime.date:
        return pl.Date
    if annotation is datetime.time:
        return pl.Time

    if origin == typing.Literal:
        return _resolve_union_types([type(arg) for arg in args])

    if origin in (types.UnionType, typing.Union):
        return _resolve_union_types(args)

    if origin == list:
        if args:
            return pl.List(python_type_to_polars(args[0], annotation))

        return pl.List(pl.Object)
    
    # Ensure that `annotation` is different from `parent_annotation` to prevent infinite recursion 
    if issubclass(annotation, BaseModel) and parent_annotation != annotation:
        return pl.Struct(pydantic_to_polars_schema(annotation))
    
    # Return the general `Object` type as fallback
    return pl.Object


def pydantic_to_polars_schema(model_class: type[BaseModel]) -> dict:
    """Convert a Pydantic model into a Polars schema"""
    schema = {}
    for field_name, field in model_class.model_fields.items():
        schema[field_name] = python_type_to_polars(field.annotation, model_class)

    return schema
