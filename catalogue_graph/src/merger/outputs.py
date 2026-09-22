"""Builds the documents the merger writes: merged works, redirects and initial images."""

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
from merger.availabilities import availabilities
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedWork,
    InvisibleIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified
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
    raise ValueError(f"Unknown work type {type(work).__name__}")


def to_redirected_work(
    source: IdentifiedWork, target: IdentifiedWork, merged_time: datetime
) -> RedirectedMergedWork:
    """A source consumed by the merge, pointing at its target; keeps its merge candidates."""
    return RedirectedMergedWork(
        version=source.version,
        state=_merged_state(source, WorkData(), merged_time),
        redirect_target=identified_id(target),
    )


def to_initial_image(
    image_data: ImageData, source: IdentifiedWork, modified_time: datetime
) -> InitialImage:
    """An image record whose parent is the work it was merged onto, minus the image data."""
    return InitialImage(
        version=image_data.version,
        locations=image_data.locations,
        source=ParentWork(
            id=identified_id(source),
            data=source.data.model_copy(update={"image_data": []}),
            version=source.version,
        ),
        modified_time=convert_datetime_to_utc_iso(modified_time),
        state=ImageState(
            canonical_id=image_data.id.canonical_id,
            source_identifier=image_data.id.source_identifier,
        ),
    )


def identified_id(work: IdentifiedWork) -> Identified:
    return Identified(
        canonical_id=work.state.canonical_id,
        source_identifier=work.state.source_identifier,
    )


def _merged_state(
    work: IdentifiedWork, data: WorkData, merged_time: datetime
) -> MergedWorkState:
    return MergedWorkState(
        source_identifier=work.state.source_identifier,
        canonical_id=work.state.canonical_id,
        merged_time=convert_datetime_to_utc_iso(merged_time),
        source_modified_time=work.state.source_modified_time,
        availabilities=availabilities(data),
        relations=work.state.relations,
        merge_candidates=work.state.merge_candidates,
    )
