import json
import os
from collections.abc import Generator
from typing import Any

import boto3
import polars as pl
import smart_open
from pydantic import BaseModel

from ingestor.extractors.base_extractor import GraphBaseExtractor
from ingestor.steps.ingestor_indexer import IngestorIndexerObject


class ElasticsearchBaseTransformer:
    def __init__(self, start_offset: int, end_index: int, is_local: bool) -> None:
        self.source: GraphBaseExtractor = GraphBaseExtractor(
            start_offset, end_index, is_local
        )

    def transform_document(self, raw_document: Any) -> BaseModel | None:
        """Accepts raw data representing the item to be transformed and returns an indexable document as a Pydantic model."""
        raise NotImplementedError(
            "Each Elasticsearch transformer must implement a `transform_document` method."
        )

    def stream_es_documents(self) -> Generator[BaseModel]:
        for raw_item in self.source.extract_raw():
            pydantic_document = self.transform_document(raw_item)
            if pydantic_document:
                yield pydantic_document

    def load_documents_to_s3(self, s3_uri: str) -> IngestorIndexerObject:
        print(f"Loading data to '{s3_uri}'...")

        es_documents = list(self.stream_es_documents())

        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_uri, "wb", transport_params=transport_params) as f:
            df = pl.DataFrame(es_documents, infer_schema_length=None)
            df.write_parquet(f)

        boto_s3_object = f.to_boto3(boto3.resource("s3"))
        content_length = boto_s3_object.content_length

        assert content_length is not None, "Content length should not be None"
        assert len(df) == len(es_documents), "DataFrame length should match data length"
        assert len(df) > 0

        return IngestorIndexerObject(
            s3_uri=s3_uri,
            content_length=content_length,
            record_count=len(df),
        )

    def load_documents_to_local_file(self, file_name: str) -> None:
        file_path = f"ingestor_outputs/{file_name}.jsonl"

        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        with open(file_path, "w") as f:
            for document in self.stream_es_documents():
                f.write(json.dumps(document.model_dump()) + "\n")
