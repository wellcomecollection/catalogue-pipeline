import os
from collections.abc import Generator
from itertools import batched
from typing import IO, Any, Literal

import boto3
import polars as pl
import pyarrow as pa
import smart_open
from pydantic import BaseModel

from ingestor.extractors.base_extractor import GraphBaseExtractor
from ingestor.models.step_events import IngestorIndexerObject, IngestorLoaderLambdaEvent
from utils.arrow import pydantic_to_pyarrow_schema

S3_BATCH_SIZE = 10_000


LoadDestination = Literal["s3", "local"]


class ElasticsearchBaseTransformer:
    def __init__(self) -> None:
        self.source: GraphBaseExtractor = GraphBaseExtractor()

    def transform_document(self, raw_item: Any) -> BaseModel | None:
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

    def stream_batches(self) -> Generator[tuple]:
        yield from batched(self.stream_es_documents(), S3_BATCH_SIZE, strict=False)

    def _load_to_parquet(self, es_documents: list[BaseModel], file: IO) -> None:
        # Convert Pydantic models to Parquet:
        # Pydantic -> dict -> PyArrow Table (with schema) -> Polars DataFrame -> Parquet
        # Explicit schema ensures reliable types (Polars inference is not reliable).
        schema = pydantic_to_pyarrow_schema(type(es_documents[0]))
        table = pa.Table.from_pylist(
            [d.model_dump(by_alias=False) for d in es_documents],
            schema=pa.schema(schema),
        )
        pl.DataFrame(table).write_parquet(file)

    def _load_to_jsonl(self, es_documents: list[BaseModel], file: IO) -> None:
        for doc in es_documents:
            line = (doc.model_dump_json() + "\n").encode("utf-8")
            file.write(line)

    def _get_file_path(
        self,
        event: IngestorLoaderLambdaEvent,
        destination: LoadDestination,
        file_name: str,
    ) -> str:
        if destination == "s3":
            full_path = event.get_s3_uri(file_name)
        elif destination == "local":
            relative_path = f"../ingestor_outputs/{file_name}.{event.load_format}"
            full_path = os.path.abspath(relative_path)
            os.makedirs(os.path.dirname(full_path), exist_ok=True)
        else:
            raise ValueError(f"Unknown destination: '{destination}'.")

        return full_path

    def load_documents(
        self,
        event: IngestorLoaderLambdaEvent,
        destination: LoadDestination = "s3",
    ) -> list[IngestorIndexerObject]:
        loaded_objects: list[IngestorIndexerObject] = []

        transport_params = None
        if destination == "s3":
            transport_params = {"client": boto3.client("s3")}

        for i, batch in enumerate(self.stream_batches()):
            file_name = f"{str(i * S3_BATCH_SIZE).zfill(8)}-{str(i * S3_BATCH_SIZE + len(batch)).zfill(8)}"
            full_path = self._get_file_path(event, destination, file_name)

            with smart_open.open(
                full_path, "wb", transport_params=transport_params
            ) as f:
                documents = list(batch)
                if event.load_format == "parquet":
                    self._load_to_parquet(documents, f)
                elif event.load_format == "jsonl":
                    self._load_to_jsonl(documents, f)

                content_length = f.tell()

            loaded_objects.append(
                IngestorIndexerObject(
                    s3_uri=full_path,
                    content_length=content_length,
                    record_count=len(documents),
                )
            )

            print(f"{len(documents)} items loaded to '{full_path}'.")

        return loaded_objects
