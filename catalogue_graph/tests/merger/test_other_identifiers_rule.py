"""Merging the other identifiers of a target and its sources."""

from merger.rules.other_identifiers import other_identifiers_rule
from merger.rules.predicates import PHYSICAL_DIGITAL_REASON
from models import identifier_schemes as schemes
from models.pipeline.format import Pictures
from models.pipeline.identified.work import IdentifiedWork, VisibleIdentifiedWork
from models.pipeline.identifier import SourceIdentifier
from tests.merger.works import (
    Maps,
    axiell_work,
    calm_work,
    digcode_identifier,
    folio_work,
    identified_work,
    invisible,
    merge_candidate,
    mets_work,
    miro_work,
    physical_item,
    same_elements,
    sierra_digital_work,
    sierra_ebsco_pair,
    sierra_physical_work,
    sierra_work,
    source_identifier,
    tei_work,
)

nothing = identified_work(schemes.NLM_MESH)
tei = tei_work()
miro = miro_work()
mets_works: list[IdentifiedWork] = [invisible(mets_work()) for _ in range(4)]
physical_sierra = sierra_physical_work(format=Pictures)
zero_item_physical_sierra = sierra_work(format=Pictures)
physical_maps_sierra = sierra_physical_work(format=Maps)
calm = calm_work(
    other_identifiers=[
        source_identifier(schemes.CALM_REF_NO),
        source_identifier(schemes.CALM_ALTREF_NO),
    ]
)
axiell = axiell_work(
    other_identifiers=[
        source_identifier(schemes.CALM_REF_NO),
        source_identifier(schemes.CALM_ALTREF_NO),
    ]
)
linked_sierra = sierra_work()
sierra_with_merge_candidate = sierra_physical_work(
    format=Pictures,
    merge_candidates=[merge_candidate(linked_sierra, PHYSICAL_DIGITAL_REASON)],
)
sierra_with_digcode = sierra_digital_work(
    other_identifiers=[
        source_identifier(schemes.SIERRA_SYSTEM_NUMBER),
        digcode_identifier("dighole"),
    ]
)


def sid(work: IdentifiedWork) -> SourceIdentifier:
    return work.state.source_identifier


def sierra_identifier(work: IdentifiedWork) -> SourceIdentifier:
    return next(
        i
        for i in work.data.other_identifiers
        if i.identifier_type.id == schemes.SIERRA_IDENTIFIER.id
    )


def with_other_identifiers(
    work: VisibleIdentifiedWork, identifiers: list[SourceIdentifier]
) -> VisibleIdentifiedWork:
    data = work.data.model_copy(update={"other_identifiers": identifiers})
    return work.model_copy(update={"data": data})


def test_mets_miro_calm_and_sierra_ids_into_a_tei_target() -> None:
    result = other_identifiers_rule.merge(
        tei, [calm, physical_sierra, nothing, miro, *mets_works]
    )

    assert same_elements(
        result.data,
        [
            sid(physical_sierra),
            sid(miro),
            *calm.data.other_identifiers,
            sid(calm),
            sierra_identifier(physical_sierra),
        ],
    )
    assert same_elements(result.sources, [physical_sierra, miro, calm])


def test_mets_miro_and_sierra_ids_into_a_calm_target() -> None:
    result = other_identifiers_rule.merge(
        calm, [physical_sierra, nothing, miro, *mets_works]
    )

    assert same_elements(
        result.data,
        [
            sid(physical_sierra),
            sid(miro),
            *(sid(mets) for mets in mets_works),
            *calm.data.other_identifiers,
            sierra_identifier(physical_sierra),
        ],
    )
    assert same_elements(result.sources, [physical_sierra, miro, *mets_works])


def test_mets_miro_and_sierra_ids_into_an_axiell_target() -> None:
    result = other_identifiers_rule.merge(
        axiell, [physical_sierra, nothing, miro, *mets_works]
    )

    assert same_elements(
        result.data,
        [
            sid(physical_sierra),
            sid(miro),
            *(sid(mets) for mets in mets_works),
            *axiell.data.other_identifiers,
            sierra_identifier(physical_sierra),
        ],
    )
    assert same_elements(result.sources, [physical_sierra, miro, *mets_works])


def test_mets_miro_axiell_and_sierra_ids_into_a_tei_target() -> None:
    result = other_identifiers_rule.merge(
        tei, [axiell, physical_sierra, nothing, miro, *mets_works]
    )

    assert same_elements(
        result.data,
        [
            sid(physical_sierra),
            sid(miro),
            *axiell.data.other_identifiers,
            sid(axiell),
            sierra_identifier(physical_sierra),
        ],
    )
    assert same_elements(result.sources, [physical_sierra, miro, axiell])


folio_hrid = source_identifier(schemes.FOLIO_INSTANCE_HRID)
folio_bnumber = source_identifier(schemes.SIERRA_SYSTEM_NUMBER)
folio = folio_work(
    items=[physical_item()],
    other_identifiers=[folio_hrid, folio_bnumber, source_identifier(schemes.ISBN)],
)


def test_a_folio_work_keeps_its_hrid_and_b_number_on_a_tei_target() -> None:
    result = other_identifiers_rule.merge(tei, [folio])

    assert same_elements(result.data, [sid(folio), folio_hrid, folio_bnumber])
    assert result.sources == [folio]


def test_a_folio_work_keeps_its_hrid_and_b_number_on_an_axiell_target() -> None:
    result = other_identifiers_rule.merge(axiell, [folio])

    assert same_elements(
        result.data,
        [*axiell.data.other_identifiers, sid(folio), folio_hrid, folio_bnumber],
    )


def test_a_b_number_is_not_kept_from_a_non_folio_source() -> None:
    axiell_with_bnumber = with_other_identifiers(
        axiell,
        [
            *axiell.data.other_identifiers,
            source_identifier(schemes.SIERRA_SYSTEM_NUMBER),
        ],
    )

    result = other_identifiers_rule.merge(tei, [axiell_with_bnumber])

    assert same_elements(result.data, [*axiell.data.other_identifiers, sid(axiell)])


def test_a_single_miro_id_into_a_single_item_sierra_work_alongside_mets() -> None:
    result = other_identifiers_rule.merge(physical_sierra, [nothing, miro, *mets_works])

    assert same_elements(
        result.data, [sid(miro), *physical_sierra.data.other_identifiers]
    )
    assert result.sources == [miro]


def test_no_miro_ids_when_there_is_more_than_one_miro_work() -> None:
    result = other_identifiers_rule.merge(physical_sierra, [nothing, miro, miro_work()])

    assert same_elements(result.data, physical_sierra.data.other_identifiers)
    assert result.sources == []


def test_several_miro_ids_into_a_digmiro_sierra_work() -> None:
    miro_2 = miro_work()
    digmiro_sierra = with_other_identifiers(
        physical_sierra, [digcode_identifier("digmiro")]
    )

    result = other_identifiers_rule.merge(digmiro_sierra, [nothing, miro, miro_2])

    assert same_elements(
        result.data, [sid(miro), sid(miro_2), *digmiro_sierra.data.other_identifiers]
    )
    assert same_elements(result.sources, [miro, miro_2])


def test_miro_ids_into_a_sierra_work_with_zero_items() -> None:
    result = other_identifiers_rule.merge(zero_item_physical_sierra, [miro])

    assert same_elements(
        result.data, [sid(miro), *zero_item_physical_sierra.data.other_identifiers]
    )
    assert result.sources == [miro]


def test_a_sierra_work_merges_into_an_ebsco_work_without_adding_ids() -> None:
    sierra, ebsco = sierra_ebsco_pair()

    result = other_identifiers_rule.merge(ebsco, [sierra])

    assert result.data == []
    assert result.sources == [sierra]


def test_no_miro_ids_into_a_sierra_work_of_another_format() -> None:
    result = other_identifiers_rule.merge(physical_maps_sierra, [nothing, miro])

    assert same_elements(result.data, physical_maps_sierra.data.other_identifiers)
    assert result.sources == []


def test_the_ids_of_a_linked_digitised_sierra_work_are_appended() -> None:
    result = other_identifiers_rule.merge(
        sierra_with_merge_candidate, [nothing, miro, linked_sierra]
    )

    assert same_elements(
        result.data,
        [
            sid(miro),
            sid(linked_sierra),
            *linked_sierra.data.other_identifiers,
            *sierra_with_merge_candidate.data.other_identifiers,
        ],
    )
    assert same_elements(result.sources, [miro, linked_sierra])


def test_only_digcodes_are_taken_from_a_source_s_other_identifiers() -> None:
    result = other_identifiers_rule.merge(calm, [sierra_with_digcode])

    assert same_elements(
        result.data,
        [
            *calm.data.other_identifiers,
            digcode_identifier("dighole"),
            sid(sierra_with_digcode),
        ],
    )


def test_only_digcodes_are_taken_onto_an_axiell_target() -> None:
    result = other_identifiers_rule.merge(axiell, [sierra_with_digcode])

    assert same_elements(
        result.data,
        [
            *axiell.data.other_identifiers,
            digcode_identifier("dighole"),
            sid(sierra_with_digcode),
        ],
    )


def test_only_the_miro_source_identifier_is_merged() -> None:
    miro_with_others = with_other_identifiers(
        miro, [source_identifier(schemes.MIRO_LIBRARY_REFERENCE)]
    )

    result = other_identifiers_rule.merge(physical_sierra, [miro_with_others])

    assert same_elements(
        result.data, [sid(miro), *physical_sierra.data.other_identifiers]
    )
    assert result.sources == [miro_with_others]


def test_no_mets_ids_are_merged() -> None:
    result = other_identifiers_rule.merge(physical_sierra, mets_works)

    assert all(i.identifier_type.id != "mets" for i in result.data)
    assert result.sources == []
