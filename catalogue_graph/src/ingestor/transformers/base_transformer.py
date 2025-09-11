import os
from collections.abc import Generator
from itertools import batched
from typing import IO, Any

import boto3
import polars as pl
import pyarrow as pa
import smart_open
from pydantic import BaseModel
from utils.arrow import pydantic_to_pyarrow_schema
from utils.types import IngestorLoadFormat

from ingestor.extractors.base_extractor import GraphBaseExtractor
from ingestor.steps.ingestor_indexer import IngestorIndexerObject

S3_BATCH_SIZE = 10_000 

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

    def stream_es_documents(self) -> Generator[BaseModel]:
        for raw_item in self.source.extract_raw():
            pydantic_document = self.transform_document(raw_item)
            if pydantic_document:
                yield pydantic_document
    
    def stream_batches(self):
        yield from batched(self.stream_es_documents(), S3_BATCH_SIZE, strict=False)
        
    def _load_to_parquet(self, es_documents: list[BaseModel], file: IO) -> int:
        # Convert Pydantic models to Parquet:
        # Pydantic -> dict -> PyArrow Table (with schema) -> Polars DataFrame -> Parquet
        # Explicit schema ensures reliable types (Polars inference is not reliable).
        schema = pydantic_to_pyarrow_schema(type(es_documents[0]))
        table = pa.Table.from_pylist(
            [d.model_dump(by_alias=False) for d in es_documents],
            schema=pa.schema(schema),
        )
        pl.DataFrame(table).write_parquet(file)

        return len(es_documents)
    
    def _load_to_jsonl(self, es_documents: Generator[BaseModel], file: IO):
        for doc in es_documents:
            line = (doc.model_dump_json() + "\n").encode("utf-8")
            file.write(line)
        
    def _load_to_file(self, es_documents: Generator[BaseModel], file: IO, load_format: IngestorLoadFormat) -> int:
        if load_format == "parquet":
            return self._load_to_parquet(list(es_documents), file)
        elif load_format == "jsonl":
            return self._load_to_jsonl(es_documents, file)
        else:
            raise ValueError(f"Unknown load file format {load_format}")

    def load_documents_to_s3(
        self, s3_prefix: str, load_format: IngestorLoadFormat
    ) -> IngestorIndexerObject:
        """Load transformed documents into a parquet file in S3."""
        transport_params = {"client": boto3.client("s3")}
        
        for i, batch in enumerate(self.stream_batches()):
            file_name = f"{str(i*S3_BATCH_SIZE).zfill(8)}-{str(i*S3_BATCH_SIZE + len(batch)).zfill(8)}"
            s3_uri = f"s3://{s3_prefix}/{file_name}"
            
            with smart_open.open(s3_uri, "wb", transport_params=transport_params) as f:
                record_count = self._load_to_file(batch, f, load_format)

            print(f"{record_count} items loaded to '{s3_uri}'.")

    def load_documents_to_local_file(
        self, file_name: str, load_format: IngestorLoadFormat
    ) -> str:
        """Load transformed documents into a local JSONL file for testing purposes."""
        file_path = f"../ingestor_outputs/{file_name}.{load_format}"
        os.makedirs(os.path.dirname(file_path), exist_ok=True)

        with open(file_path, "wb") as f:
            self._load_to_file(self.stream_es_documents(), f, load_format)

        return os.path.abspath(file_path)
