"""Merges the works of one component.

One work becomes the target and takes fields from the others. The sources a rule
claimed are redirected to the target; the rest are left untouched. A TEI work's
internal works are emitted alongside it, and deleted when their stub goes.
"""

from collections.abc import Sequence

import structlog

from ingestor.models.shared.deleted_reason import TeiDeletedInMerger
from merger.merge_result import (
    MergerOutcome,
    merge_fields,
    target_only_merge_result,
)
from merger.rules.base import distinct_works
from merger.rules.precedence import first_by_precedence
from merger.rules.predicates import lms_work, lms_work_digitised_av, miro_work
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedWork,
    IdentifiedWorkState,
    InternalWorkStub,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified

logger = structlog.get_logger(__name__)


def merge_component(
    ids: Sequence[str], works: Sequence[IdentifiedWork]
) -> MergerOutcome:
    """Merges the works of a component, or passes them through unmerged when any
    of its works could not be read."""
    if len(works) < len(ids):
        return MergerOutcome.pass_through(works)
    return merge(works)


def merge(works: Sequence[IdentifiedWork]) -> MergerOutcome:
    outcomes = [
        _merge_group(group)
        for miro_group in _partition_shared_miro(works)
        for group in _partition_audiovisual(miro_group)
    ]
    result_works = [work for outcome in outcomes for work in outcome.works]
    return MergerOutcome(
        works=result_works + _deleted_internal_works(works, result_works),
        images=[image for outcome in outcomes for image in outcome.images],
    )


def _partition_shared_miro(
    works: Sequence[IdentifiedWork],
) -> list[list[IdentifiedWork]]:
    """Bibs linked only through a shared Miro image must not pool their METS or
    Axiell works on one target. See platform issue 6750."""
    miro_works = [work for work in works if miro_work(work)]
    others = [work for work in works if not miro_work(work)]

    # Group the other works by their direct links to each other.
    components: list[list[IdentifiedWork]] = []
    for work in others:
        touching = [
            c
            for c in components
            if any(_linked(w, work) or _linked(work, w) for w in c)
        ]
        apart = [c for c in components if not any(c is t for t in touching)]
        components = [[w for c in touching for w in c] + [work], *apart]

    def links_to(component: list[IdentifiedWork], miro: IdentifiedWork) -> bool:
        return any(_linked(work, miro) for work in component)

    # Works the matcher grouped without a direct link stay with the target.
    bridged = [c for c in components if any(links_to(c, m) for m in miro_works)]
    target = first_by_precedence(works)
    if target is None or len(bridged) <= 1:
        return [list(works)]

    carved = [c for c in bridged if not _contains(c, target)]
    # A Miro work goes to whichever group linking it wins target precedence.
    miro_owner: dict[str, list[IdentifiedWork]] = {}
    for miro in miro_works:
        linking = [c for c in bridged if links_to(c, miro)]
        winner = first_by_precedence(
            [w for w in works if any(_contains(c, w) for c in linking)]
        )
        owner = next((c for c in linking if _contains(c, winner)), None)
        if owner is not None:
            miro_owner[miro.state.canonical_id] = owner

    group_of: dict[str, int] = {}
    for index, component in enumerate(carved):
        own_miro = [
            m for m in miro_works if miro_owner.get(m.state.canonical_id) is component
        ]
        for work in [*component, *own_miro]:
            group_of[work.state.canonical_id] = index

    remainder = [w for w in works if w.state.canonical_id not in group_of]
    groups = [
        [w for w in works if group_of.get(w.state.canonical_id) == index]
        for index in range(len(carved))
    ]
    return [remainder, *groups]


def _partition_audiovisual(
    works: Sequence[IdentifiedWork],
) -> list[list[IdentifiedWork]]:
    """AV bibs are never merged with each other, but their 776 links still put a
    physical bib and all of its e-bibs in one component. Each AV e-bib is carved
    out with the works linked directly to it. See platform issue 6643."""
    target = first_by_precedence(works)
    if len(works) < 2 or target is None or not lms_work(target):
        return [list(works)]

    av_ebibs = [work for work in works if lms_work_digitised_av(work)]
    others = [work for work in works if not lms_work(work)]

    owner_of: dict[str, str] = {}
    for other in others:
        linked = [e for e in av_ebibs if _linked(other, e) or _linked(e, other)]
        if len(linked) == 1:
            owner_of[other.state.canonical_id] = linked[0].state.canonical_id
        elif len(linked) > 1:
            logger.warning(
                "Work is linked to several audiovisual e-bibs, leaving it in the main group",
                work=other.state.canonical_id,
                ebibs=[e.state.canonical_id for e in linked],
            )

    groups = []
    for ebib in av_ebibs:
        attached = [
            o
            for o in others
            if owner_of.get(o.state.canonical_id) == ebib.state.canonical_id
        ]
        if attached:
            groups.append([ebib, *attached])

    carved = {work.state.canonical_id for group in groups for work in group}
    remainder = [work for work in works if work.state.canonical_id not in carved]
    return [*groups, remainder] if remainder else groups


def _merge_group(works: Sequence[IdentifiedWork]) -> MergerOutcome:
    if len(works) == 1 and isinstance(works[0], VisibleIdentifiedWork):
        result = target_only_merge_result(works[0])
        return MergerOutcome(
            works=[*_internal_works(result.merged_target), result.merged_target],
            images=result.images,
        )

    target = first_by_precedence(works)
    if target is None:
        return MergerOutcome.pass_through(works)
    deleted = [w for w in works if isinstance(w, DeletedIdentifiedWork)]
    sources = [
        w
        for w in works
        if not isinstance(w, DeletedIdentifiedWork)
        and w.state.source_identifier != target.state.source_identifier
    ]

    redirected, result = merge_fields(target, sources)
    remaining = [s for s in sources if not _contains(redirected, s)]
    logger.info(
        "Merged works",
        target=target.state.canonical_id,
        redirected=[s.state.canonical_id for s in redirected],
        remaining=[s.state.canonical_id for s in remaining],
        images=len(result.images),
    )

    merged = result.merged_target
    target_work = merged.model_copy(
        update={
            "redirect_sources": merged.redirect_sources
            + [_identified(s) for s in redirected]
        }
    )
    return MergerOutcome(
        works=[
            *(_redirect(s, target) for s in redirected),
            *remaining,
            *deleted,
            *_internal_works(merged),
            target_work,
        ],
        images=result.images,
    )


def _identified(work: IdentifiedWork) -> Identified:
    return Identified(
        canonical_id=work.state.canonical_id,
        source_identifier=work.state.source_identifier,
    )


def _linked(a: IdentifiedWork, b: IdentifiedWork) -> bool:
    return any(
        candidate.id.canonical_id == b.state.canonical_id
        for candidate in a.state.merge_candidates
    )


def _contains(works: Sequence[IdentifiedWork], work: IdentifiedWork | None) -> bool:
    return work is not None and any(
        w.state.canonical_id == work.state.canonical_id for w in works
    )


def _redirect(
    source: IdentifiedWork, target: VisibleIdentifiedWork
) -> RedirectedIdentifiedWork:
    return RedirectedIdentifiedWork(
        version=source.version,
        state=IdentifiedWorkState(
            source_identifier=source.state.source_identifier,
            canonical_id=source.state.canonical_id,
            source_modified_time=source.state.source_modified_time,
            merge_candidates=source.state.merge_candidates,
        ),
        redirect_target=_identified(target),
    )


def _internal_works(work: VisibleIdentifiedWork) -> list[VisibleIdentifiedWork]:
    """The works nested in a TEI work, each given its parent's thumbnail."""
    return [
        VisibleIdentifiedWork(
            version=work.version,
            data=stub.work_data.model_copy(update={"thumbnail": work.data.thumbnail}),
            state=IdentifiedWorkState(
                source_identifier=stub.source_identifier,
                canonical_id=stub.canonical_id,
                source_modified_time=work.state.source_modified_time,
            ),
        )
        for stub in work.state.internal_work_stubs
    ]


def _deleted_internal_works(
    works: Sequence[IdentifiedWork], result_works: Sequence[IdentifiedWork]
) -> list[IdentifiedWork]:
    """Deletes for internal works whose parent was deleted or whose stub was removed.

    Removals are read from the works going in, as a TEI work that loses the merge is
    redirected and its stubs dropped on the way. They are never pruned, so a parent
    re-emits them on every merge; an id that moved to another manuscript flips between
    deleted and visible depending on which parent was touched last.
    """
    emitted = {work.state.canonical_id for work in result_works}
    deleted_parents = [
        _delete_child(parent, stub)
        for parent in result_works
        if isinstance(parent, DeletedIdentifiedWork)
        for stub in parent.state.internal_work_stubs
    ]
    removed = [
        _delete_child(parent, stub)
        for parent in works
        for stub in parent.state.removed_internal_work_stubs
    ]
    return distinct_works(
        d for d in deleted_parents + removed if d.state.canonical_id not in emitted
    )


def _delete_child(
    parent: IdentifiedWork, stub: InternalWorkStub
) -> DeletedIdentifiedWork:
    return DeletedIdentifiedWork(
        version=parent.version,
        state=IdentifiedWorkState(
            source_identifier=stub.source_identifier,
            canonical_id=stub.canonical_id,
            source_modified_time=parent.state.source_modified_time,
        ),
        deleted_reason=TeiDeletedInMerger(),
    )
