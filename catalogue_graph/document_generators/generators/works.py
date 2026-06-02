from ingestor.models.merged.work import MergedWorkState, VisibleMergedWork
from models.pipeline.concept import Contributor, Genre, Subject
from models.pipeline.id_label import Language
from models.pipeline.production import ProductionEvent
from models.pipeline.work_data import WorkData
from models.pipeline.work_state import WorkRelations

from .concepts import create_source_identifier
from .random import random_alphanumeric, random_canonical_id


def create_work_data(
    title: str | None = None,
    contributors: list[Contributor] | None = None,
    genres: list[Genre] | None = None,
    subjects: list[Subject] | None = None,
    languages: list[Language] | None = None,
    production: list[ProductionEvent] | None = None,
) -> WorkData:
    return WorkData(
        title=title or random_alphanumeric(15),
        contributors=contributors or [],
        genres=genres or [],
        subjects=subjects or [],
        languages=languages or [],
        production=production or [],
    )


def create_merged_work_state() -> MergedWorkState:
    return MergedWorkState(
        source_identifier=create_source_identifier(),
        canonical_id=random_canonical_id(),
        merged_time="2025-01-01T00:00:00Z",
        source_modified_time="2025-01-01T00:00:00Z",
        availabilities=[],
        merge_candidates=[],
        relations=WorkRelations(),
    )


def create_visible_merged_work(
    title: str | None = None,
    contributors: list[Contributor] | None = None,
    genres: list[Genre] | None = None,
    subjects: list[Subject] | None = None,
    languages: list[Language] | None = None,
    production: list[ProductionEvent] | None = None,
) -> VisibleMergedWork:
    return VisibleMergedWork(
        version=1,
        data=create_work_data(
            title=title,
            contributors=contributors,
            genres=genres,
            subjects=subjects,
            languages=languages,
            production=production,
        ),
        state=create_merged_work_state(),
    )
