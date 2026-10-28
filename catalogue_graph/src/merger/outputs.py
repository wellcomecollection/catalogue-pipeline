"""Builds the documents the merger writes: merged works and initial images."""

from datetime import datetime

from inferrer.models import InitialImage
from ingestor.models.augmented.image import ParentWork
from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    MergedWork,
    MergedWorkState,
    RedirectedMergedWork,
    VisibleMergedWork,
)
from ingestor.models.shared.merge_candidate import MergeCandidate
from merger.availabilities import availabilities
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedWork,
    InvisibleIdentifiedWork,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.image import ImageData
from models.pipeline.image_state import ImageState
from models.pipeline.work_data import WorkData
from utils.timezone import convert_datetime_to_utc_iso


def to_merged_work(work: IdentifiedWork, merged_time: datetime) -> MergedWork:
    """The Identified to Merged transition: same document, merged state, availabilities derived."""
    state = _merged_state(work, work.data, merged_time)
    if isinstance(work, VisibleIdentifiedWork):
        return VisibleMergedWork(
            version=work.version,
            data=work.data,
            state=state,
            redirect_sources=work.redirect_sources,
        )
    if isinstance(work, InvisibleIdentifiedWork):
        return InvisibleMergedWork(
            version=work.version,
            data=work.data,
            state=state,
            invisibility_reasons=work.invisibility_reasons,
        )
    if isinstance(work, DeletedIdentifiedWork):
        return DeletedMergedWork(
            version=work.version, state=state, deleted_reason=work.deleted_reason
        )
    if isinstance(work, RedirectedIdentifiedWork):
        return RedirectedMergedWork(
            version=work.version, state=state, redirect_target=work.redirect_target
        )
    raise ValueError(f"Unknown work type {type(work).__name__}")


def to_initial_image(
    image: ImageData, parent: ParentWork, modified_time: datetime
) -> InitialImage:
    return InitialImage(
        version=image.version,
        locations=image.locations,
        source=parent,
        modified_time=convert_datetime_to_utc_iso(modified_time),
        state=ImageState(
            canonical_id=image.id.canonical_id,
            source_identifier=image.id.source_identifier,
        ),
    )


def _merged_state(
    work: IdentifiedWork, data: WorkData, merged_time: datetime
) -> MergedWorkState:
    merge_candidates: list[MergeCandidate] = list(work.state.merge_candidates)
    return MergedWorkState(
        source_identifier=work.state.source_identifier,
        canonical_id=work.state.canonical_id,
        merged_time=convert_datetime_to_utc_iso(merged_time),
        source_modified_time=work.state.source_modified_time,
        availabilities=availabilities(data),
        relations=work.state.relations,
        merge_candidates=merge_candidates,
    )
