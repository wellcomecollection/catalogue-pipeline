"""Manifest-related pydantic models shared by transformer & other steps."""

from __future__ import annotations

from pydantic import BaseModel


class S3Location(BaseModel):
    bucket: str
    key: str


class SuccessManifest(BaseModel):
    count: int
    batch_file_location: S3Location


class FailureManifest(BaseModel):
    count: int
    error_file_location: S3Location


class TransformerManifest(BaseModel):
    successes: SuccessManifest
    failures: FailureManifest | None = None
