"""
Creates example work documents for catalogue API tests using deterministic random data via a seeded RNG.

Run from the catalogue_graph directory:
    uv run python -m document_generators.create_test_work_documents
"""

from collections.abc import Sequence
from typing import Any

from freezegun import freeze_time

from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.indexable.work import (
    DeletedIndexableWork,
    InvisibleIndexableWork,
    RedirectedIndexableWork,
    VisibleIndexableWork,
)
from ingestor.models.merged.work import (
    DeletedMergedWork,
    InvisibleMergedWork,
    MergedWork,
    RedirectedMergedWork,
    VisibleMergedWork,
)
from ingestor.models.neptune.query_result import WorkHierarchy
from models.pipeline.access_condition import (
    AccessCondition,
    AccessMethod,
    AccessStatus,
    AccessStatusRelationship,
)
from models.pipeline.collection_path import CollectionPath
from models.pipeline.concept import Concept, Genre, Period, Subject
from models.pipeline.format import (
    FORMAT_LABEL_MAPPING,
    Audio,
    Books,
    Format,
    Journals,
    Pictures,
)
from models.pipeline.id_label import Id, Language
from models.pipeline.identifier import Identified, SourceIdentifier
from models.pipeline.production import ProductionEvent

from .generators import (
    create_closed_stores_item,
    create_concept,
    create_contributor,
    create_deleted_merged_work,
    create_digital_item,
    create_genre,
    create_genre_concept,
    create_holdings,
    create_identified,
    create_image_data,
    create_invisible_merged_work,
    create_item,
    create_language,
    create_note,
    create_open_shelves_item,
    create_period_for_year,
    create_period_for_year_range,
    create_production_event,
    create_redirected_merged_work,
    create_source_identifier,
    create_subject,
    create_unidentifiable_item,
    create_visible_merged_work,
    random_alphanumeric,
    reset,
)
from .generators.locations import create_digital_location, create_physical_location
from .utils import TEST_DOCUMENTS_DIR, save_document

ALL_FORMATS = [Format(id=k, label=v) for k, v in FORMAT_LABEL_MAPPING.items()]


@freeze_time("2001-01-01T01:01:01Z")
def transform_visible_work(work: VisibleMergedWork) -> dict[str, Any]:
    hierarchy = WorkHierarchy(id=work.state.canonical_id, ancestors=[], children=[])
    extracted = VisibleExtractedWork(work=work, hierarchy=hierarchy, concepts=[])
    indexable = VisibleIndexableWork.from_extracted_work(extracted)
    return indexable.model_dump(mode="json", exclude_none=True)


@freeze_time("2001-01-01T01:01:01Z")
def transform_invisible_work(work: InvisibleMergedWork) -> dict[str, Any]:
    indexable = InvisibleIndexableWork.from_merged_work(work)
    return indexable.model_dump(mode="json", exclude_none=True)


@freeze_time("2001-01-01T01:01:01Z")
def transform_redirected_work(work: RedirectedMergedWork) -> dict[str, Any]:
    indexable = RedirectedIndexableWork.from_merged_work(work)
    return indexable.model_dump(mode="json", exclude_none=True)


@freeze_time("2001-01-01T01:01:01Z")
def transform_deleted_work(work: DeletedMergedWork) -> dict[str, Any]:
    indexable = DeletedIndexableWork.from_merged_work(work)
    return indexable.model_dump(mode="json", exclude_none=True)


def transform_work(work: MergedWork) -> dict[str, Any]:
    if isinstance(work, VisibleMergedWork):
        return transform_visible_work(work)
    elif isinstance(work, InvisibleMergedWork):
        return transform_invisible_work(work)
    elif isinstance(work, RedirectedMergedWork):
        return transform_redirected_work(work)
    elif isinstance(work, DeletedMergedWork):
        return transform_deleted_work(work)
    else:
        raise ValueError(f"Unknown work type: {type(work)}")


def save_works(works: Sequence[MergedWork], description: str, doc_id: str) -> None:
    if len(works) == 1:
        work = works[0]
        document = transform_work(work)
        work_id = work.state.canonical_id
        save_document(doc_id, description, work_id, document)
    else:
        for index, work in enumerate(works):
            document = transform_work(work)
            work_id = work.state.canonical_id
            save_document(f"{doc_id}.{index}", description, work_id, document)


def save_work(work: MergedWork, description: str, doc_id: str) -> None:
    save_works([work], description, doc_id)


# ---------- Test document generators ----------


def create_works_of_different_types() -> None:
    visible = sorted(
        [create_visible_merged_work() for _ in range(5)],
        key=lambda w: w.state.canonical_id,
    )
    save_works(
        visible,
        description="an arbitrary list of visible works",
        doc_id="works.visible",
    )
    save_works(
        [create_invisible_merged_work() for _ in range(3)],
        description="an arbitrary list of invisible works",
        doc_id="works.invisible",
    )
    save_works(
        [create_redirected_merged_work() for _ in range(2)],
        description="an arbitrary list of redirected works",
        doc_id="works.redirected",
    )
    save_works(
        [create_deleted_merged_work() for _ in range(4)],
        description="an arbitrary list of deleted works",
        doc_id="works.deleted",
    )


def create_works_with_optional_fields() -> None:
    save_work(
        work=create_visible_merged_work(edition="Special edition", duration=3600),
        description="a work with optional top-level fields",
        doc_id="work-with-edition-and-duration",
    )

    save_work(
        work=create_visible_merged_work(
            thumbnail=create_digital_location(location_type_id="thumbnail-image")
        ),
        description="a work with a thumbnail",
        doc_id="work-thumbnail",
    )


def create_works_with_specific_titles() -> None:
    save_work(
        work=create_visible_merged_work(
            title="A drawing of a dodo", lettering="A line of legible ligatures"
        ),
        description="a work with 'dodo' in the title",
        doc_id="work-title-dodo",
    )
    save_work(
        work=create_visible_merged_work(
            title="A mezzotint of a mouse",
            lettering="A print of proportional penmanship",
        ),
        description="a work with 'mouse' in the title",
        doc_id="work-title-mouse",
    )


def create_works_with_production_events() -> None:
    for year in ["1900", "1976", "1904", "2020", "1098"]:
        production = [
            ProductionEvent(
                label=random_alphanumeric(25),
                places=[],
                agents=[],
                dates=[create_period_for_year(year)],
            )
        ]
        save_work(
            work=create_visible_merged_work(
                title=f"Production event in {year}",
                production=production,
            ),
            description=f"a work with a production event in {year}",
            doc_id=f"work-production.{year}",
        )

    save_work(
        work=create_invisible_merged_work(),
        description="an invisible work with 'mouse' in the title",
        doc_id="work.invisible.title-mouse",
    )


def create_works_with_all_includes() -> None:
    works = []
    for _ in range(3):
        work = create_visible_merged_work(
            title="A work with all the include-able fields",
            other_identifiers=[create_source_identifier()],
            subjects=[
                create_subject(f"subject-{random_alphanumeric()}") for _ in range(2)
            ],
            genres=[create_genre(f"genre-{random_alphanumeric()}") for _ in range(2)],
            contributors=[
                create_contributor(
                    f"person-{random_alphanumeric()}", concept_type="Person"
                )
                for _ in range(2)
            ],
            production=[create_production_event() for _ in range(2)],
            languages=[create_language() for _ in range(3)],
            notes=[create_note() for _ in range(4)],
            image_data=[create_image_data() for _ in range(2)],
            holdings=create_holdings(3),
            former_frequency=["Published in 2001", "Published in 2002"],
            designation=["Designation #1", "Designation #2", "Designation #3"],
            items=[create_item() for _ in range(2)] + [create_unidentifiable_item()],
        )
        works.append(work)

    save_works(
        works,
        description="a list of work with all the include-able fields",
        doc_id="work.visible.everything",
    )

    # Format filter/aggregation tests
    formats = [Books] * 4 + [Journals] * 3 + [Audio] * 2 + [Pictures]
    for i, fmt in enumerate(formats):
        save_work(
            work=create_visible_merged_work(
                title=f"A work with format {fmt.label}", format=fmt
            ),
            description="one of a list of works with a variety of formats",
            doc_id=f"works.formats.{i}.{fmt.label}",
        )

    save_work(
        work=create_visible_merged_work(
            title="+a -title | with (all the simple) query~4 syntax operators in it*"
        ),
        description="a work whose title has lots of ES query syntax operators",
        doc_id="works.title-query-syntax",
    )
    save_work(
        work=create_visible_merged_work(title="(a b c d e) h"),
        description="a work whose title has parens meant to trip up ES",
        doc_id="works.title-query-parens",
    )

    # Language filter/aggregation tests
    english = Language(id="eng", label="English")
    swedish = Language(id="swe", label="Swedish")
    turkish = Language(id="tur", label="Turkish")

    language_combos = [
        [english],
        [english],
        [english],
        [english, swedish],
        [english, swedish, turkish],
        [swedish],
        [turkish],
    ]
    for i, languages in enumerate(language_combos):
        label = ", ".join(lang.label for lang in languages)
        lang_id = "+".join(lang.id for lang in languages)
        save_work(
            work=create_visible_merged_work(
                title=f"A work with languages {label}", languages=languages
            ),
            description="one of a list of works with a variety of languages",
            doc_id=f"works.languages.{i}.{lang_id}",
        )


def create_works_with_licenses() -> None:
    license_combos: list[list[str]] = [
        ["cc-by"],
        ["cc-by"],
        ["cc-by-nc"],
        ["cc-by", "cc-by-nc"],
        [],
    ]
    works = []
    for licenses in license_combos:
        items = [create_digital_item(license_id=lic) for lic in licenses]
        works.append(create_visible_merged_work(items=items))

    save_works(
        works,
        description="a work with licensed digital items",
        doc_id="works.items-with-licenses",
    )


def create_works_with_genres() -> None:
    concept0 = Concept(
        id=Identified(
            canonical_id="concept0a",
            source_identifier=create_source_identifier(ontology_type="Genre"),
        ),
        label="Conceptual Conversations",
        type="GenreConcept",
    )
    concept1 = Concept(
        id=Identified(
            canonical_id="concept1a",
            source_identifier=create_source_identifier(ontology_type="Concept"),
        ),
        label="Pleasant Paris",
        type="Place",
    )
    concept2 = Period(
        id=Identified(
            canonical_id=create_identified().canonical_id,
            source_identifier=create_source_identifier(ontology_type="Period"),
        ),
        label="Past Prehistory",
        range=None,
    )

    genre = Genre(
        label="Electronic books",
        concepts=[concept0, concept1, concept2],
    )

    save_work(
        work=create_visible_merged_work(
            title="A work with different concepts in the genre",
            genres=[genre],
        ),
        description="a work with different concepts in the genre",
        doc_id="works.genres",
    )


def create_works_with_subjects() -> None:
    paleo_neuro_biology = create_subject("paleoNeuroBiology")
    real_analysis = create_subject("realAnalysis")

    subject_lists: list[list[Subject]] = [
        [paleo_neuro_biology],
        [real_analysis],
        [real_analysis],
        [paleo_neuro_biology, real_analysis],
        [],
    ]

    works = [
        create_visible_merged_work(subjects=subjects) for subjects in subject_lists
    ]
    save_works(
        works,
        description="works with different subjects",
        doc_id="works.subjects",
    )


def create_works_with_contributors() -> None:
    agent47 = create_contributor("47", concept_type="Agent")
    james_bond = create_contributor("007", concept_type="Agent")
    mi5 = create_contributor("MI5", concept_type="Organisation")
    gchq = create_contributor("GCHQ", concept_type="Organisation")

    agent_combos = [
        [agent47],
        [agent47],
        [james_bond, mi5],
        [mi5, gchq],
    ]

    works = [create_visible_merged_work(contributors=agents) for agents in agent_combos]
    save_works(
        works,
        description="works with different contributor",
        doc_id="works.contributor",
    )


def create_works_with_item_identifiers() -> None:
    works = []
    for _ in range(5):
        item = create_item()
        works.append(create_visible_merged_work(items=[item]))

    save_works(
        works,
        description="works with items with other identifiers",
        doc_id="works.items-with-other-identifiers",
    )


def create_works_with_collection_paths() -> None:
    save_work(
        work=create_visible_merged_work(
            collection_path=CollectionPath(path="PPCRI", label="PP/CRI")
        ),
        description="a work with a collection path",
        doc_id="works.collection-path.PPCRI",
    )
    save_work(
        work=create_visible_merged_work(
            collection_path=CollectionPath(path="NUFFINK", label="NUF/FINK")
        ),
        description="a work with a collection path",
        doc_id="works.collection-path.NUFFINK",
    )


def create_works_with_every_format() -> None:
    works = [create_visible_merged_work(format=fmt) for fmt in ALL_FORMATS]
    save_works(
        works,
        description="works with every format",
        doc_id="works.every-format",
    )


def create_works_with_production_periods() -> None:
    periods = [
        create_period_for_year("1850"),
        create_period_for_year_range("1850", "2000"),
        create_period_for_year_range("1860", "1960"),
        create_period_for_year("1960"),
        create_period_for_year_range("1960", "1964"),
        create_period_for_year("1962"),
    ]

    works = [
        create_visible_merged_work(
            production=[
                ProductionEvent(
                    label=random_alphanumeric(25),
                    places=[create_concept()],
                    agents=[create_concept()],
                    dates=[p],
                )
            ]
        )
        for p in periods
    ]
    save_works(
        works,
        description="works with multi-year production ranges",
        doc_id="works.production.multi-year",
    )


def create_works_with_location_types() -> None:
    items = [
        create_item(locations=[create_digital_location(location_type_id="iiif-image")]),
        create_item(
            locations=[
                create_digital_location(location_type_id="iiif-image"),
                create_digital_location(location_type_id="iiif-presentation"),
            ]
        ),
        create_item(
            locations=[create_physical_location(location_type_id="closed-stores")]
        ),
    ]

    works = [create_visible_merged_work(items=[item]) for item in items]
    save_works(
        works,
        description="items with different location types",
        doc_id="work.items-with-location-types",
    )


def create_works_for_aggregation_with_filters() -> None:
    subjects = [create_subject(f"agg-subject-{i}") for i in range(6)]
    works = []
    for i, fmt in enumerate(ALL_FORMATS):
        work = create_visible_merged_work(
            format=fmt,
            subjects=[subjects[i % len(subjects)]],
        )
        works.append(work)

    save_works(
        works,
        description="examples for the aggregation-with-filters tests",
        doc_id="works.examples.aggregation-with-filters-tests",
    )


def create_works_of_different_work_types() -> None:
    for work_type in ["Section", "Collection", "Series"]:
        save_work(
            work=create_visible_merged_work(title="rats", work_type=work_type),  # type: ignore[arg-type]
            description="examples of works with different types",
            doc_id=f"works.examples.different-work-types.{work_type}",
        )


def create_genre_filter_test_examples() -> None:
    annual_reports = create_genre(
        "Annual reports",
        concepts=[create_genre_concept("g00dcafe"), create_concept("baadf00d")],
    )
    pamphlets = create_genre("Pamphlets", concepts=[create_genre_concept("g00dcafe")])
    psychology = create_genre(
        "Psychology, Pathological",
        concepts=[create_genre_concept("baadf00d")],
    )
    darwin = create_genre('Darwin "Jones", Charles')

    works = [
        create_visible_merged_work(genres=[annual_reports]),
        create_visible_merged_work(genres=[pamphlets]),
        create_visible_merged_work(genres=[psychology]),
        create_visible_merged_work(genres=[darwin]),
        create_visible_merged_work(genres=[pamphlets, psychology, darwin]),
        create_visible_merged_work(),
    ]
    save_works(
        works,
        description="examples for the genre tests",
        doc_id="works.examples.genre-filters-tests",
    )


def create_subject_filter_test_examples() -> None:
    sanitation = Subject(
        id=Identified(
            canonical_id="sanitati",
            source_identifier=SourceIdentifier(
                identifier_type=Id(id="lc-subjects"),
                value="lcsh-sanitation",
                ontology_type="Subject",
            ),
            other_identifiers=[
                SourceIdentifier(
                    identifier_type=Id(id="nlm-mesh"),
                    value="mesh-sanitation",
                    ontology_type="Subject",
                )
            ],
        ),
        label="Sanitation.",
        concepts=[create_concept(), create_concept()],
    )

    london = create_subject("London (England)")
    psychology = create_subject("Psychology, Pathological")

    darwin = Subject(
        id=Identified(
            canonical_id="darwin01",
            source_identifier=SourceIdentifier(
                identifier_type=Id(id="lc-names"),
                value="lcnames-darwin",
                ontology_type="Subject",
            ),
        ),
        label='Darwin "Jones", Charles',
        concepts=[create_concept(), create_concept()],
    )

    works = [
        create_visible_merged_work(subjects=[sanitation]),
        create_visible_merged_work(subjects=[london]),
        create_visible_merged_work(subjects=[psychology]),
        create_visible_merged_work(subjects=[darwin]),
        create_visible_merged_work(subjects=[london, psychology, darwin]),
        create_visible_merged_work(),
    ]
    save_works(
        works,
        description="examples for the subject filter tests",
        doc_id="works.examples.subject-filters-tests",
    )


def create_contributor_filter_test_examples() -> None:
    patricia = create_contributor("Bath, Patricia", concept_type="Person")
    karl_marx = create_contributor("Karl Marx", concept_type="Person")
    jake_paul = create_contributor("Jake Paul", concept_type="Person")
    darwin = create_contributor('Darwin "Jones", Charles', concept_type="Person")

    works = [
        create_visible_merged_work(contributors=[patricia]),
        create_visible_merged_work(contributors=[karl_marx]),
        create_visible_merged_work(contributors=[jake_paul]),
        create_visible_merged_work(contributors=[darwin]),
        create_visible_merged_work(contributors=[patricia, darwin]),
        create_visible_merged_work(contributors=[]),
    ]
    save_works(
        works,
        description="examples for the contributor filter tests",
        doc_id="works.examples.contributor-filters-tests",
    )


def create_access_status_filter_test_examples() -> None:
    def work_with_access_status(status: AccessStatus) -> VisibleMergedWork:
        loc = create_digital_location(
            location_type_id="iiif-image",
            access_conditions=[
                AccessCondition(
                    method=AccessMethod(type="ManualRequest"),
                    status=status,
                )
            ],
        )
        item = create_item(locations=[loc])
        return create_visible_merged_work(items=[item])

    works = [
        work_with_access_status(AccessStatus(type="Restricted")),
        work_with_access_status(AccessStatus(type="Restricted")),
        work_with_access_status(AccessStatus(type="Closed")),
        work_with_access_status(AccessStatus(type="Open")),
        work_with_access_status(AccessStatus(type="OpenWithAdvisory")),
        work_with_access_status(
            AccessStatus(
                type="LicensedResources",
                relationship=AccessStatusRelationship(type="Resource"),
            )
        ),
        work_with_access_status(
            AccessStatus(
                type="LicensedResources",
                relationship=AccessStatusRelationship(type="RelatedResource"),
            )
        ),
    ]
    save_works(
        works,
        description="examples for the access status tests",
        doc_id="works.examples.access-status-filters-tests",
    )


def create_filtered_aggregations_test_examples() -> None:
    bashkir = Language(id="bak", label="Bashkir")
    marathi = Language(id="mar", label="Marathi")
    quechua = Language(id="que", label="Quechua")
    chechen = Language(id="che", label="Chechen")

    # | workType     | count |
    # |--------------|-------|
    # | a / Books    | 4     |
    # | d / Journals | 3     |
    # | i / Audio    | 2     |
    # | k / Pictures | 1     |
    #
    # | language       | count |
    # |----------------|-------|
    # | bak / Bashkir  | 4     |
    # | que / Quechua  | 3     |
    # | mar / Marathi  | 2     |
    # | che / Chechen  | 1     |
    combos = [
        (Books, bashkir, "rats"),
        (Journals, marathi, "capybara"),
        (Pictures, quechua, "tapirs"),
        (Audio, bashkir, "rats"),
        (Books, bashkir, "capybara"),
        (Books, bashkir, "tapirs"),
        (Journals, quechua, "rats"),
        (Books, marathi, "capybara"),
        (Journals, quechua, "tapirs"),
        (Audio, chechen, "rats"),
    ]

    works = [
        create_visible_merged_work(title=title, format=fmt, languages=[lang])
        for fmt, lang, title in combos
    ]
    save_works(
        works,
        description="examples for the works filtered aggregations tests",
        doc_id="works.examples.filtered-aggregations-tests",
    )


def create_availabilities_test_examples() -> None:
    examples = {
        "closed-only": create_visible_merged_work(
            items=[create_closed_stores_item()],
            availabilities=[Id(id="closed-stores")],
        ),
        "online-only": create_visible_merged_work(
            items=[create_digital_item(access_status_type="Open")],
            availabilities=[Id(id="online")],
        ),
        "open-only": create_visible_merged_work(
            items=[create_open_shelves_item()],
            availabilities=[Id(id="open-shelves")],
        ),
        "everywhere": create_visible_merged_work(
            items=[
                create_closed_stores_item(),
                create_open_shelves_item(),
                create_digital_item(access_status_type="OpenWithAdvisory"),
            ],
            availabilities=[
                Id(id="closed-stores"),
                Id(id="open-shelves"),
                Id(id="online"),
            ],
        ),
        "nowhere": create_visible_merged_work(),
    }

    for suffix, work in examples.items():
        save_work(
            work=work,
            description="examples for availabilities tests",
            doc_id=f"works.examples.availabilities.{suffix}",
        )


def create_works_with_digital_location_dates() -> None:
    years = ["2020", "2021", "2022"]
    for year in years:
        loc = create_digital_location(
            location_type_id="iiif-image",
            license_id="cc-by",
            created_date=f"{year}-01-15T10:00:00Z",
        )
        item = create_item(locations=[loc])
        save_work(
            work=create_visible_merged_work(
                title=f"Digital location created in {year}",
                items=[item],
                availabilities=[Id(id="online")],
            ),
            description=f"a work with a digital location created in {year}",
            doc_id=f"work-digital-location.{year}",
        )

    save_work(
        work=create_visible_merged_work(
            title="A work with no digital location",
        ),
        description="a work with no digital location",
        doc_id="work-digital-location.no-date",
    )


def generate_all() -> None:
    reset()

    create_works_of_different_types()
    create_works_with_optional_fields()
    create_works_with_specific_titles()
    create_works_with_production_events()
    create_works_with_all_includes()
    create_works_with_licenses()
    create_works_with_genres()
    create_works_with_subjects()
    create_works_with_contributors()
    create_works_with_item_identifiers()
    create_works_with_collection_paths()
    create_works_with_every_format()
    create_works_with_production_periods()
    create_works_with_location_types()
    create_works_for_aggregation_with_filters()
    create_works_of_different_work_types()
    create_genre_filter_test_examples()
    create_subject_filter_test_examples()
    create_contributor_filter_test_examples()
    create_access_status_filter_test_examples()
    create_filtered_aggregations_test_examples()
    create_availabilities_test_examples()
    create_works_with_digital_location_dates()

    print(f"Test documents written to {TEST_DOCUMENTS_DIR}")


if __name__ == "__main__":
    generate_all()
