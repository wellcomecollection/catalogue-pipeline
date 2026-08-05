"""Models for the image inference manager and its work-discovery step.

These mirror the Scala `inference_manager` data model and the catalogue
`internal_model` image lifecycle:

    Image[Initial]  --(inferrer)-->  Image[Augmented]

The read model (`InitialImage`) is parsed from the `images-initial` index; the
write path reuses `ingestor.models.augmented.image.AugmentedImage` (which now
carries an optional `state.augmentedTime`) to serialise into `images-augmented`,
rather than maintaining a parallel write model here.
"""

from __future__ import annotations

from pydantic import BaseModel

import config

# InferredData and ParentWork are shared with the ingestor read path.
from ingestor.models.augmented.image import (
    ParentWork,
)
from models.events import BasePipelineEvent
from models.find_work import FindWorkEvent as BaseFindWorkEvent
from models.pipeline.image_state import ImageState
from models.pipeline.location import DigitalLocation
from models.pipeline.serialisable import SerialisableModel

DEFAULT_PARTITION_SIZE = 300


# --- Read model: a document from the `images-initial` index ---------------- #


class InitialImage(SerialisableModel):
    state: ImageState
    source: ParentWork
    locations: list[DigitalLocation]
    version: int
    modified_time: str


# --- Step events ----------------------------------------------------------- #


class InferenceManagerEvent(BasePipelineEvent):
    """Input for a single inference task: a partition of image ids to augment.

    Inherits `ids` / `window` / `pipeline_date` / `index_dates` from
    `BasePipelineEvent`; an inference task is normally invoked in `ids` mode with
    a partition produced by the find-work step.
    """


class FindWorkEvent(BaseFindWorkEvent):
    """Input for the work-discovery step: a time window (or ids/full)."""

    partition_size: int = DEFAULT_PARTITION_SIZE

    @property
    def s3_service_prefix_parts(self) -> list[str]:
        return [config.INFERRER_S3_PREFIX, "find_work"]


# --- Step results ---------------------------------------------------------- #


class InferenceManagerResult(BaseModel):
    # All-or-nothing except permanently undownloadable assets, which are
    # skipped and counted, so processed = augmented + download_failure_count.
    processed: int
    augmented: int
    download_failure_count: int = 0


class FindWorkResult(BaseModel):
    partitions: list[InferenceManagerEvent]
