"""Merges the works of one component.

One work becomes the target and takes fields from the others. The sources a rule
drew on are redirected to the target; the rest are left untouched. A TEI work's
internal works are emitted alongside it, and deleted when their stub goes.
"""

from abc import ABC, abstractmethod
from collections.abc import Sequence
from dataclasses import dataclass

import structlog

from ingestor.models.augmented.image import ParentWork
from ingestor.models.shared.deleted_reason import TeiDeletedInMerger
from merger.rules.base import distinct_works
from merger.rules.images import ImageDataRule, ImagesRule
from merger.rules.items import ItemsRule
from merger.rules.other_identifiers import OtherIdentifiersRule
from merger.rules.predicates import (
    lms_work,
    lms_work_digitised_av,
    miro_work,
    single_digital_item_miro_work,
)
from merger.rules.support import with_items_in_internal_works
from merger.rules.target_precedence import choose_target
from merger.rules.thumbnail import ThumbnailRule
from models.pipeline.identified.work import (
    DeletedIdentifiedWork,
    IdentifiedWork,
    IdentifiedWorkState,
    InternalWorkStub,
    RedirectedIdentifiedWork,
    VisibleIdentifiedWork,
)
from models.pipeline.identifier import Identified
from models.pipeline.image import ImageData
from models.pipeline.item import Item

logger = structlog.get_logger(__name__)


@dataclass(frozen=True)
class ImageWithParent:
    image: ImageData
    parent: ParentWork


@dataclass(frozen=True)
class MergeResult:
    """A target with every field merged, and the images created on the way."""

    target: VisibleIdentifiedWork
    images: list[ImageWithParent]


@dataclass(frozen=True)
class MergerOutcome:
    """Every work to emit for a component, and the images with their parent work."""

    works: list[IdentifiedWork]
    images: list[ImageWithParent]


def identified_id(work: IdentifiedWork) -> Identified:
    return Identified(
        canonical_id=work.state.canonical_id,
        source_identifier=work.state.source_identifier,
    )


def parent_work(work: VisibleIdentifiedWork) -> ParentWork:
    return ParentWork(
        id=identified_id(work),
        data=work.data.model_copy(update={"image_data": []}),
        version=work.version,
    )


def _linked(a: IdentifiedWork, b: IdentifiedWork) -> bool:
    return any(
        candidate.id.canonical_id == b.state.canonical_id
        for candidate in a.state.merge_candidates
    )


def _contains(works: Sequence[IdentifiedWork], work: IdentifiedWork) -> bool:
    return any(w.state.canonical_id == work.state.canonical_id for w in works)


class Merger(ABC):
    """Merges a component given a way to choose the target and to merge its fields."""

    @classmethod
    @abstractmethod
    def find_target(
        cls, works: Sequence[IdentifiedWork]
    ) -> VisibleIdentifiedWork | None: ...

    @classmethod
    @abstractmethod
    def create_merge_result(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> tuple[list[IdentifiedWork], MergeResult]:
        """The merged target and images, with the sources to redirect to it."""

    @classmethod
    def merge(cls, works: Sequence[IdentifiedWork]) -> MergerOutcome:
        outcomes = [
            cls._merge_group(group)
            for miro_group in cls._partition_shared_miro(works)
            for group in cls._partition_audiovisual(miro_group)
        ]
        result_works = [work for outcome in outcomes for work in outcome.works]
        return MergerOutcome(
            works=result_works + _deleted_internal_works(works, result_works),
            images=[image for outcome in outcomes for image in outcome.images],
        )

    @classmethod
    def _partition_shared_miro(
        cls, works: Sequence[IdentifiedWork]
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
        target = cls.find_target(works)
        if target is None or len(bridged) <= 1:
            return [list(works)]

        carved = [c for c in bridged if not _contains(c, target)]
        # A Miro work goes to whichever group linking it wins target precedence.
        miro_owner: dict[str, list[IdentifiedWork]] = {}
        for miro in miro_works:
            linking = [c for c in bridged if links_to(c, miro)]
            linking_works = [w for w in works if any(_contains(c, w) for c in linking)]
            winner = cls.find_target(linking_works)
            if winner is None:
                continue
            owner = next((c for c in linking if _contains(c, winner)), None)
            if owner is not None:
                miro_owner[miro.state.canonical_id] = owner

        group_of: dict[str, int] = {}
        for index, component in enumerate(carved):
            own_miro = [
                m
                for m in miro_works
                if miro_owner.get(m.state.canonical_id) is component
            ]
            for work in [*component, *own_miro]:
                group_of[work.state.canonical_id] = index

        remainder = [w for w in works if w.state.canonical_id not in group_of]
        groups = [
            [w for w in works if group_of.get(w.state.canonical_id) == index]
            for index in range(len(carved))
        ]
        return [remainder, *groups]

    @classmethod
    def _partition_audiovisual(
        cls, works: Sequence[IdentifiedWork]
    ) -> list[list[IdentifiedWork]]:
        """AV bibs are never merged with each other, but their 776 links still put a
        physical bib and all of its e-bibs in one cluster. Each AV e-bib is carved
        out with the works linked directly to it. See platform issue 6643."""
        target = cls.find_target(works)
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
        return groups if not remainder else [*groups, remainder]

    @classmethod
    def _merge_group(cls, works: Sequence[IdentifiedWork]) -> MergerOutcome:
        if len(works) == 1 and isinstance(works[0], VisibleIdentifiedWork):
            result = cls._target_only(works[0])
            return MergerOutcome(
                works=[*_internal_works(result.target), result.target],
                images=result.images,
            )

        target = cls.find_target(works)
        if target is None:
            return MergerOutcome(works=list(works), images=[])
        deleted = [w for w in works if isinstance(w, DeletedIdentifiedWork)]
        sources = [
            w
            for w in works
            if not isinstance(w, DeletedIdentifiedWork)
            and w.state.source_identifier != target.state.source_identifier
        ]

        redirected, result = cls.create_merge_result(target, sources)
        remaining = [s for s in sources if not _contains(redirected, s)]
        logger.info(
            "Merged works",
            target=target.state.canonical_id,
            redirected=[s.state.canonical_id for s in redirected],
            remaining=[s.state.canonical_id for s in remaining],
        )

        target_work = result.target.model_copy(
            update={
                "redirect_sources": result.target.redirect_sources
                + [identified_id(s) for s in redirected]
            }
        )
        return MergerOutcome(
            works=[
                *(_redirect(s, target) for s in redirected),
                *remaining,
                *deleted,
                *_internal_works(result.target),
                target_work,
            ],
            images=result.images,
        )

    @classmethod
    def _target_only(cls, target: VisibleIdentifiedWork) -> MergeResult:
        """A lone work still has its items settled, and a Miro work emits its image."""
        items = ItemsRule.merge(target, []).data
        images = target.data.image_data if single_digital_item_miro_work(target) else []
        return MergeResult(
            target=_with_items(target, items),
            images=[ImageWithParent(image, parent_work(target)) for image in images],
        )


class PlatformMerger(Merger):
    @classmethod
    def find_target(
        cls, works: Sequence[IdentifiedWork]
    ) -> VisibleIdentifiedWork | None:
        return choose_target(works)

    @classmethod
    def create_merge_result(
        cls, target: VisibleIdentifiedWork, sources: Sequence[IdentifiedWork]
    ) -> tuple[list[IdentifiedWork], MergeResult]:
        items = ItemsRule.merge(target, sources)
        thumbnail = ThumbnailRule.merge(target, sources)
        other_identifiers = OtherIdentifiersRule.merge(target, sources)
        image_data = ImageDataRule.merge(target, sources)
        images = ImagesRule.merge(target, sources)

        data = target.data.model_copy(
            update={
                "items": items.data,
                "thumbnail": thumbnail.data,
                "other_identifiers": other_identifiers.data,
                "image_data": image_data.data,
            }
        )
        work = target.model_copy(update={"data": data})
        redirected = distinct_works(
            [
                *items.redirected_sources,
                *thumbnail.redirected_sources,
                *other_identifiers.redirected_sources,
                *image_data.redirected_sources,
                *images.redirected_sources,
            ]
        )
        result = MergeResult(
            target=with_items_in_internal_works(work, items.data),
            images=[ImageWithParent(image, parent_work(work)) for image in images.data],
        )
        return redirected, result


def _with_items(
    work: VisibleIdentifiedWork, items: list[Item]
) -> VisibleIdentifiedWork:
    data = work.data.model_copy(update={"items": items})
    return with_items_in_internal_works(work.model_copy(update={"data": data}), items)


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
        redirect_target=identified_id(target),
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
        [d for d in deleted_parents + removed if d.state.canonical_id not in emitted]
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
