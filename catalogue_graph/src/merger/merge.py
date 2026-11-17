"""Merges the works of one component.

One work becomes the target and takes fields from the others. The sources a rule
claimed are redirected to the target; the rest are left untouched. A TEI work's
internal works are emitted alongside it, and deleted when their stub goes.
"""

from collections.abc import Sequence
from dataclasses import dataclass

import structlog

from ingestor.models.shared.deleted_reason import TeiDeletedInMerger
from merger.merged_target import (
    ImageWithSource,
    merge_fields,
    merge_lone_target,
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


@dataclass(frozen=True)
class MergedComponent:
    """Every work and image record a component's merge produces."""

    works: list[IdentifiedWork]
    images: list[ImageWithSource]


def merge_component(works: Sequence[IdentifiedWork]) -> MergedComponent:
    """Merges the works of one component, splitting it into parts first where needed.

    The rules take the first of several equally good works, so the works are sorted by
    canonical id first, to give the same result whatever order they arrive in.
    """
    works = sorted(works, key=lambda work: work.state.canonical_id)
    merged_parts = [
        _merge_part(part)
        for miro_part in _partition_shared_miro(works)
        for part in _partition_audiovisual(miro_part)
    ]
    result_works = [work for merged in merged_parts for work in merged.works]
    return MergedComponent(
        works=result_works + _deleted_internal_works(works, result_works),
        images=[image for merged in merged_parts for image in merged.images],
    )


def _partition_shared_miro(
    works: Sequence[IdentifiedWork],
) -> list[list[IdentifiedWork]]:
    """Bibs linked only through a shared Miro image must not pool their METS or
    Axiell works on one target. See platform issue 6750."""
    miro_works = [work for work in works if miro_work(work)]
    others = [work for work in works if not miro_work(work)]

    # Cluster the other works by their direct links to each other.
    clusters: list[list[IdentifiedWork]] = []
    for work in others:
        touching = [
            c for c in clusters if any(_linked(w, work) or _linked(work, w) for w in c)
        ]
        apart = [c for c in clusters if not any(c is t for t in touching)]
        clusters = [[w for c in touching for w in c] + [work], *apart]

    def links_to(cluster: list[IdentifiedWork], miro: IdentifiedWork) -> bool:
        return any(_linked(work, miro) for work in cluster)

    # Clusters that link to no Miro work stay with the target.
    bridged = [c for c in clusters if any(links_to(c, m) for m in miro_works)]
    target = first_by_precedence(works)
    if target is None or len(bridged) <= 1:
        return [list(works)]

    carved = [c for c in bridged if target not in c]
    # A Miro work goes to whichever cluster linking it wins target precedence.
    miro_owner: dict[str, list[IdentifiedWork]] = {}
    for miro in miro_works:
        linking = [c for c in bridged if links_to(c, miro)]
        winner = first_by_precedence([w for w in works if any(w in c for c in linking)])
        owner = next((c for c in linking if winner in c), None)
        if owner is not None:
            miro_owner[miro.state.canonical_id] = owner

    part_of: dict[str, int] = {}
    for index, cluster in enumerate(carved):
        own_miro = [
            m for m in miro_works if miro_owner.get(m.state.canonical_id) is cluster
        ]
        for work in [*cluster, *own_miro]:
            part_of[work.state.canonical_id] = index

    remainder = [w for w in works if w.state.canonical_id not in part_of]
    parts = [
        [w for w in works if part_of.get(w.state.canonical_id) == index]
        for index in range(len(carved))
    ]
    return [remainder, *parts]


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
                "Work is linked to several audiovisual e-bibs, leaving it in the main part",
                work=other.state.canonical_id,
                ebibs=[e.state.canonical_id for e in linked],
            )

    parts = []
    for ebib in av_ebibs:
        attached = [
            o
            for o in others
            if owner_of.get(o.state.canonical_id) == ebib.state.canonical_id
        ]
        if attached:
            parts.append([ebib, *attached])

    carved = {work.state.canonical_id for part in parts for work in part}
    remainder = [work for work in works if work.state.canonical_id not in carved]
    return [*parts, remainder] if remainder else parts


def _merge_part(works: Sequence[IdentifiedWork]) -> MergedComponent:
    if len(works) == 1 and isinstance(works[0], VisibleIdentifiedWork):
        result = merge_lone_target(works[0])
        return MergedComponent(
            works=[*_internal_works(result.target), result.target],
            images=result.images,
        )

    target = first_by_precedence(works)
    if target is None:
        return MergedComponent(works=list(works), images=[])
    deleted = [w for w in works if isinstance(w, DeletedIdentifiedWork)]
    sources = [
        w
        for w in works
        if not isinstance(w, DeletedIdentifiedWork)
        and w.state.source_identifier != target.state.source_identifier
    ]

    result = merge_fields(target, sources)
    remaining = [s for s in sources if s not in result.claimed]
    logger.info(
        "Merged works",
        target=target.state.canonical_id,
        redirected=[s.state.canonical_id for s in result.claimed],
        remaining=[s.state.canonical_id for s in remaining],
        images=len(result.images),
    )

    merged = result.target
    target_work = merged.model_copy(
        update={
            "redirect_sources": merged.redirect_sources
            + [_identified(s) for s in result.claimed]
        }
    )
    return MergedComponent(
        works=[
            *(_redirect(s, target) for s in result.claimed),
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
