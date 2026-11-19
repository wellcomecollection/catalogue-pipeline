"""Merges the works of one component.

One work becomes the target and takes fields from the others. The sources a rule
claimed are redirected to the target, and the rest are left untouched. A TEI work's
internal works are returned alongside it, and deleted when the TEI work is deleted
or no longer lists them.
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
    clusters = _linked_clusters([work for work in works if not miro_work(work)])
    # Clusters that list no Miro work stay with the target.
    miro_clusters = [
        c for c in clusters if any(_cluster_points_to(c, m) for m in miro_works)
    ]
    target = first_by_precedence(works)
    if target is None or len(miro_clusters) <= 1:
        return [list(works)]

    parts = []
    for cluster in miro_clusters:
        if target not in cluster:
            owned = [
                m
                for m in miro_works
                if _owning_cluster(works, miro_clusters, m) is cluster
            ]
            members = {w.state.canonical_id for w in [*cluster, *owned]}
            # In the component's order, which breaks precedence ties.
            parts.append([w for w in works if w.state.canonical_id in members])
    return [_unassigned(works, parts), *parts]


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
    attached: dict[str, list[IdentifiedWork]] = {
        ebib.state.canonical_id: [] for ebib in av_ebibs
    }
    for work in works:
        if lms_work(work):
            continue
        linked = [ebib for ebib in av_ebibs if _linked(work, ebib)]
        if len(linked) == 1:
            attached[linked[0].state.canonical_id].append(work)
        elif len(linked) > 1:
            logger.warning(
                "Work is linked to several audiovisual e-bibs, leaving it in the main part",
                work=work.state.canonical_id,
                ebibs=[ebib.state.canonical_id for ebib in linked],
            )

    parts = []
    for ebib in av_ebibs:
        if attached[ebib.state.canonical_id]:
            parts.append([ebib, *attached[ebib.state.canonical_id]])
    unassigned = _unassigned(works, parts)
    return [*parts, unassigned] if unassigned else parts


def _linked_clusters(works: Sequence[IdentifiedWork]) -> list[list[IdentifiedWork]]:
    """The works grouped so that two works share a cluster when a chain of direct
    links joins them."""
    clusters: list[list[IdentifiedWork]] = []
    for work in works:
        joined = [c for c in clusters if any(_linked(w, work) for w in c)]
        separate = [c for c in clusters if not any(c is j for j in joined)]
        clusters = [[w for c in joined for w in c] + [work], *separate]
    return clusters


def _owning_cluster(
    works: Sequence[IdentifiedWork],
    clusters: list[list[IdentifiedWork]],
    miro: IdentifiedWork,
) -> list[IdentifiedWork] | None:
    """Of the clusters listing the Miro work, the one holding the work that wins
    target precedence."""
    listing = [c for c in clusters if _cluster_points_to(c, miro)]
    listed = {w.state.canonical_id for c in listing for w in c}
    winner = first_by_precedence([w for w in works if w.state.canonical_id in listed])
    return next((c for c in listing if winner in c), None)


def _unassigned(
    works: Sequence[IdentifiedWork], parts: list[list[IdentifiedWork]]
) -> list[IdentifiedWork]:
    """The works in none of the parts."""
    assigned = {work.state.canonical_id for part in parts for work in part}
    return [work for work in works if work.state.canonical_id not in assigned]


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


def _points_to(a: IdentifiedWork, b: IdentifiedWork) -> bool:
    """Whether a lists b as a merge candidate."""
    return any(
        candidate.id.canonical_id == b.state.canonical_id
        for candidate in a.state.merge_candidates
    )


def _linked(a: IdentifiedWork, b: IdentifiedWork) -> bool:
    """Whether either work lists the other as a merge candidate."""
    return _points_to(a, b) or _points_to(b, a)


def _cluster_points_to(cluster: Sequence[IdentifiedWork], work: IdentifiedWork) -> bool:
    """Whether any work in the cluster lists the work as a merge candidate."""
    return any(_points_to(member, work) for member in cluster)


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
    re-emits them on every merge. An id that moved to another manuscript flips between
    deleted and visible depending on which parent was touched last.
    """
    deletes: list[IdentifiedWork] = []
    for parent in result_works:
        if isinstance(parent, DeletedIdentifiedWork):
            deletes += [
                _delete_child(parent, s) for s in parent.state.internal_work_stubs
            ]
    for parent in works:
        deletes += [
            _delete_child(parent, s) for s in parent.state.removed_internal_work_stubs
        ]
    emitted = {work.state.canonical_id for work in result_works}
    return distinct_works(d for d in deletes if d.state.canonical_id not in emitted)


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
