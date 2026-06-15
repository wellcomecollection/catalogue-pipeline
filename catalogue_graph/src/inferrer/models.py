"""Models for the image inference manager and its work-discovery step.

These mirror the Scala `inference_manager` data model and the catalogue
`internal_model` image lifecycle:

    Image[Initial]  --(inferrer)-->  Image[Augmented]

The read model (`InitialImage`) is parsed from the `images-initial` index; the
write model (`AugmentedImageToIndex`) is serialised into `images-augmented`.
We deliberately do NOT reuse `ingestor.models.augmented.image.AugmentedImage`
for writing: that read model omits `state.augmentedTime`, which downstream graph
steps query on, so writing through it would drop the field.
"""

from __future__ import annotations

from pydantic import BaseModel

# InferredData and ParentWork are shared with the ingestor read path.
from ingestor.models.augmented.image import (
    AugmentedImageState,
    ParentWork,
)
from models.events import BasePipelineEvent
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


# --- Write model: a document for the `images-augmented` index --------------- #


class AugmentedImageStateToIndex(AugmentedImageState):
    # `AugmentedImageState` already carries `inferred_data`; the augmented state
    # additionally records when augmentation happened (serialised as
    # `state.augmentedTime`), which the graph pipeline uses for its incremental
    # window queries.
    augmented_time: str


class AugmentedImageToIndex(SerialisableModel):
    state: AugmentedImageStateToIndex
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


class FindWorkEvent(BasePipelineEvent):
    """Input for the work-discovery step: a time window (or ids/full)."""

    partition_size: int = DEFAULT_PARTITION_SIZE


# --- Step results ---------------------------------------------------------- #


class InferenceManagerResult(BaseModel):
    # An inference task is all-or-nothing: if any requested image cannot be
    # fully and validly augmented the task fails, so on success `processed`
    # (initial images found) equals `augmented` (documents written).
    processed: int
    augmented: int


class FindWorkResult(BaseModel):
    partitions: list[InferenceManagerEvent]
