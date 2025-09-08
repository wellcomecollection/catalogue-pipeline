import json
import os
from collections.abc import Generator
from typing import IO, Any

import boto3
import polars as pl
import pyarrow as pa
import smart_open
from pydantic import BaseModel

from ingestor.extractors.base_extractor import GraphBaseExtractor
from ingestor.steps.ingestor_indexer import IngestorIndexerObject
from utils.arrow import pydantic_to_pyarrow_schema
from utils.types import IngestorLoadFormat


class ElasticsearchBaseTransformer:
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source: GraphBaseExtractor = GraphBaseExtractor(
            start_offset, end_index, is_local
        )

    def transform_document(self, raw_document: Any) -> BaseModel | None:
        """
        Accepts raw data representing the item to be transformed and returns an indexable document as a Pydantic model.
        """
        raise NotImplementedError(
            "Each Elasticsearch transformer must implement a `transform_document` method."
        )

    def _load_to_file(self, file: IO, load_format: IngestorLoadFormat) -> int:
        es_documents = list(self.stream_es_documents())
        if len(es_documents) == 0:
            raise ValueError("No documents to write.")

        if load_format == "parquet":
            # Convert Pydantic models to Parquet:
            # Pydantic -> dict -> PyArrow Table (with schema) -> Polars DataFrame -> Parquet
            # Explicit schema ensures reliable types (Polars inference is not reliable).
            schema = pydantic_to_pyarrow_schema(type(es_documents[0]))
            table = pa.Table.from_pylist(
                [d.model_dump(by_alias=False) for d in es_documents],
                schema=pa.schema(schema),
            )
            pl.DataFrame(table).write_parquet(file)
        elif load_format == "jsonl":
            for doc in es_documents:
                line = (json.dumps(doc.model_dump()) + "\n").encode("utf-8")
                file.write(line)
        else:
            raise ValueError(f"Unknown load file format {load_format}")

        return len(es_documents)

    def stream_es_documents(self) -> Generator[BaseModel]:
        for raw_item in self.source.extract_raw():
            pydantic_document = self.transform_document(raw_item)
            if pydantic_document:
                yield pydantic_document

    def load_documents_to_s3(
        self, s3_uri: str, load_format: IngestorLoadFormat
    ) -> IngestorIndexerObject:
        """Load transformed documents into a parquet file in S3."""
        print(f"Loading data to '{s3_uri}'...")

        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_uri, "wb", transport_params=transport_params) as f:
            record_count = self._load_to_file(f, load_format)

        boto_s3_object = f.to_boto3(boto3.resource("s3"))
        content_length = boto_s3_object.content_length

        print(f"Data loaded to '{s3_uri}' with content length {content_length}")

        return IngestorIndexerObject(
            s3_uri=s3_uri,
            content_length=content_length,
            record_count=record_count,
        )

    def load_documents_to_local_file(
        self, file_name: str, load_format: IngestorLoadFormat
    ) -> str:
        """Load transformed documents into a local JSONL file for testing purposes."""
        file_path = f"../ingestor_outputs/{file_name}.{load_format}"
        os.makedirs(os.path.dirname(file_path), exist_ok=True)

        with open(file_path, "wb") as f:
            self._load_to_file(f, load_format)

        return os.path.abspath(file_path)
