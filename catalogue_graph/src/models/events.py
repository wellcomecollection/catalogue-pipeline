from datetime import datetime
from typing import Literal

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


class IncrementalWindow(BaseModel):
    start_time: datetime
    end_time: datetime


class ExtractorEvent(BaseModel):
    transformer_type: TransformerType
    entity_type: EntityType
    stream_destination: StreamDestination
    pipeline_date: str | None = None
    sample_size: int | None = None
    window: IncrementalWindow | None = None
