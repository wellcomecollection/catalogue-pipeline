import argparse
from datetime import datetime
from typing import Literal, Self

from pydantic import BaseModel

TransformerType = Literal[
    "loc_concepts",
    "loc_names",
    "loc_locations",
    "mesh_concepts",
    "mesh_locations",
    "wikidata_linked_loc_concepts",
    "wikidata_linked_loc_locations",
    "wikidata_linked_loc_names",
    "wikidata_linked_mesh_concepts",
    "wikidata_linked_mesh_locations",
    "catalogue_concepts",
    "catalogue_works",
    "catalogue_work_identifiers",
]
EntityType = Literal["nodes", "edges"]
StreamDestination = Literal["graph", "s3", "sns", "local", "void"]


DEFAULT_INSERT_ERROR_THRESHOLD = 1 / 10000


class IncrementalWindow(BaseModel):
    start_time: datetime
    end_time: datetime


class GraphPipelineEvent(BaseModel):
    transformer_type: TransformerType
    entity_type: EntityType
    window: IncrementalWindow | None = None

    @classmethod
    def from_argparser(cls, args: argparse.Namespace) -> Self:
        window = None
        if args.window_start is not None and args.window_end is not None:
            window = IncrementalWindow(
                start_time=args.window_start, end_time=args.window_end
            )

        return cls(**args.__dict__, window=window)


class ExtractorEvent(GraphPipelineEvent):
    stream_destination: StreamDestination
    pipeline_date: str | None = None
    sample_size: int | None = None


class BulkLoaderEvent(GraphPipelineEvent):
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD


class BulkLoadPollerEvent(BaseModel):
    load_id: str
    insert_error_threshold: float = DEFAULT_INSERT_ERROR_THRESHOLD
