package weco.pipeline.merger.services

import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState
}
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work._
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.{
  ImageDataWithSource,
  MergeResult,
  MergerOutcome,
  TargetOnlyMergeResult,
  WorkMergingOps
}
import weco.pipeline.merger.rules._
import weco.pipeline.merger.rules.WorkPredicates.{sierraDigitisedAv, sierraWork}

/*
 * The implementor of a Merger must provide:
 * - `findTarget`, which finds the target from the input works
 * - `createMergeResult`, a recipe for creating a merged target and a
 *   map with keys of works used in the merge and values of whether they
 *   should be redirected
 *
 * Calling `merge` with a list of works will return a new list of works including:
 * - the target work with all fields merged
 * - all redirected sources
 * - any other works untouched
 */
trait Merger extends MergerLogging {
  type MergeState = Map[Work[Identified], Boolean]

  protected def findTarget(
    works: Seq[Work[Identified]]
  ): Option[Work.Visible[Identified]]

  protected def createMergeResult(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): (Seq[Work[Identified]], MergeResult)

  private case class CategorisedWorks(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]] = Nil,
    deleted: Seq[Work.Deleted[Identified]] = Nil
  ) {
    require(!sources.contains(target))
    require(deleted.intersect(sources).isEmpty)
  }

  private def categoriseWorks(
    works: Seq[Work[Identified]]
  ): Option[CategorisedWorks] =
    works match {
      case List(unmatchedWork: Work.Visible[Identified]) =>
        Some(CategorisedWorks(target = unmatchedWork))
      case matchedWorks =>
        findTarget(matchedWorks).map {
          target =>
            CategorisedWorks(
              target = target,
              sources = matchedWorks
                .filterNot { _.isInstanceOf[Work.Deleted[Identified]] }
                .filterNot { _.sourceIdentifier == target.sourceIdentifier },
              deleted = matchedWorks.collect {
                case w: Work.Deleted[Identified] =>
                  w
              }
            )
        }
    }

  def merge(works: Seq[Work[Identified]]): MergerOutcome = {
    val outcomes = partitionAudiovisual(works).map(mergeWorks)
    val resultWorks = outcomes.flatMap(_.resultWorks)
    MergerOutcome(
      resultWorks = resultWorks ++ deletedInternalWorks(works, resultWorks),
      imagesWithSources = outcomes.flatMap(_.imagesWithSources)
    )
  }

  /** AV bibs are never merged with each other, but their 776 links still put a
    * physical bib and all of its e-bibs in one cluster, so whole-cluster
    * merging elected one arbitrary e-bib and gave it every METS manifest. Carve
    * each AV e-bib out with the works linked directly to it and merge the rest
    * as before.
    *
    * See https://github.com/wellcomecollection/platform/issues/6643
    */
  private def partitionAudiovisual(
    works: Seq[Work[Identified]]
  ): Seq[Seq[Work[Identified]]] =
    if (works.size < 2 || !findTarget(works).exists(sierraWork)) {
      Seq(works)
    } else {
      val avEbibs = works.filter(sierraDigitisedAv)
      val others = works.filterNot(sierraWork)

      def linked(a: Work[Identified], b: Work[Identified]): Boolean =
        a.state.mergeCandidates.exists(_.id.canonicalId == b.state.canonicalId)

      val ownerOf: Map[CanonicalId, CanonicalId] = others.flatMap {
        other =>
          avEbibs.filter(e => linked(other, e) || linked(e, other)) match {
            case Seq(single) =>
              Some(other.state.canonicalId -> single.state.canonicalId)
            case Nil => None
            case several =>
              warn(
                s"${describeWork(other)} is linked to several audiovisual e-bibs, leaving it in the main group: ${describeWorks(several)}"
              )
              None
          }
      }.toMap

      val groups = avEbibs.flatMap {
        ebib =>
          val attached = others.filter(
            o =>
              ownerOf.get(o.state.canonicalId).contains(ebib.state.canonicalId)
          )
          if (attached.isEmpty) None else Some(ebib +: attached)
      }
      val carved = groups.flatten.map(_.state.canonicalId).toSet
      val remainder = works.filterNot(w => carved.contains(w.state.canonicalId))

      if (remainder.isEmpty) groups else groups :+ remainder
    }

  private def mergeWorks(works: Seq[Work[Identified]]): MergerOutcome = {
    works match {
      case Seq(target: Work.Visible[Identified]) =>
        logIntentions(target, Nil)
        val result = TargetOnlyMergeResult(target)
        logResult(result, Nil, Nil)
        val internalWorks = result.mergedTarget.internalWorks
        MergerOutcome(
          resultWorks = internalWorks :+ result.mergedTarget,
          imagesWithSources = result.imageDataWithSources
        )
      case _ =>
        categoriseWorks(works)
          .map {
            case CategorisedWorks(target, sources, deleted) =>
              assert((sources ++ deleted :+ target).toSet == works.toSet)

              logIntentions(target, sources)
              val (redirectedSources, result) =
                createMergeResult(target, sources)

              val remaining = sources.toSet -- redirectedSources
              val redirects =
                redirectedSources.map(redirectSourceToTarget(target))
              logResult(result, redirects.toList, remaining.toList)

              val redirectedIdentifiers =
                redirectedSources.map {
                  s =>
                    IdState.Identified(s.state.canonicalId, s.sourceIdentifier)
                }

              val internalWorks = result.mergedTarget.internalWorks

              val targetWork: Work.Visible[Identified] =
                Work.Visible[Identified](
                  version = result.mergedTarget.version,
                  data = result.mergedTarget.data,
                  state = result.mergedTarget.state,
                  redirectSources =
                    result.mergedTarget.redirectSources ++ redirectedIdentifiers
                )

              MergerOutcome(
                resultWorks =
                  redirects.toList ++ remaining ++ deleted ++ internalWorks :+ targetWork,
                imagesWithSources = result.imageDataWithSources
              )
          }
          .getOrElse(MergerOutcome.passThrough(works))
    }
  }

  private implicit class WorkOps(w: Work.Visible[Identified]) {
    def internalWorks: List[Work.Visible[Identified]] =
      internalWorksWith(thumbnail = w.data.thumbnail, version = w.version)

    private def internalWorksWith(
      thumbnail: Option[DigitalLocation],
      version: Int
    ): List[Work.Visible[Identified]] =
      w.state.internalWorkStubs.map {
        case InternalWork.Identified(sourceIdentifier, canonicalId, data) =>
          Work.Visible[Identified](
            version = version,
            data = data.copy(thumbnail = thumbnail),
            state = WorkState.Identified(
              sourceIdentifier = sourceIdentifier,
              canonicalId = canonicalId,
              sourceModifiedTime = w.state.sourceModifiedTime
            )
          )
      }
  }

  /** Inner works are synthesised from the parent's stubs on every merge, so an
    * inner work only stops being made when its stub goes. Both ways that
    * happens, the whole parent being deleted and a single stub being removed
    * from it, leave a work behind that has to be deleted explicitly.
    *
    * Removals are read from the works going in rather than the ones coming out,
    * because a TEI work that loses the merge is redirected and its stubs are
    * dropped on the way.
    *
    * WARNING: removals are never pruned, so a parent re-emits a delete for
    * every stub it has ever lost, on every merge. That is harmless for an id
    * that was renamed and never seen again, but wrong for one that MOVED to
    * another manuscript under the same id: this parent deletes it on every
    * update, while the new parent only revives it when the new parent itself
    * changes, so the work flips between deleted and visible depending on which
    * was touched last. Fixing it properly needs state this stage does not have,
    * either a record of which removals have already been actioned or a lookup
    * of who currently claims the id. Worth designing in when the merger is
    * rewritten in Python rather than bolting onto this one.
    */
  private def deletedInternalWorks(
    works: Seq[Work[Identified]],
    resultWorks: Seq[Work[Identified]]
  ): Seq[Work.Deleted[Identified]] = {
    val alreadyEmitted = resultWorks.map(_.state.canonicalId).toSet

    val deletedParents = resultWorks
      .collect { case w: Work.Deleted[Identified] => w }
      .flatMap(
        parent => parent.state.internalWorkStubs.map(deleteChildOf(parent))
      )

    val removedStubs = works
      .flatMap(
        parent =>
          parent.state.removedInternalWorkStubs.map(deleteChildOf(parent))
      )

    (deletedParents ++ removedStubs).filterNot {
      child => alreadyEmitted.contains(child.state.canonicalId)
    }.distinct
  }

  private def deleteChildOf(
    parent: Work[Identified]
  )(stub: InternalWork.Identified): Work.Deleted[Identified] =
    Work.Deleted[Identified](
      version = parent.version,
      state = Identified(
        sourceIdentifier = stub.sourceIdentifier,
        canonicalId = stub.canonicalId,
        sourceModifiedTime = parent.state.sourceModifiedTime
      ),
      deletedReason = DeletedReason.TeiDeletedInMerger
    )

  private def redirectSourceToTarget(
    target: Work.Visible[Identified]
  )(source: Work[Identified]): Work.Redirected[Identified] =
    Work.Redirected[Identified](
      version = source.version,
      state = Identified(
        sourceIdentifier = source.sourceIdentifier,
        canonicalId = source.state.canonicalId,
        sourceModifiedTime = source.state.sourceModifiedTime,
        mergeCandidates = source.state.mergeCandidates,
        internalWorkStubs = Nil
      ),
      redirectTarget =
        IdState.Identified(target.state.canonicalId, target.sourceIdentifier)
    )

  private def logIntentions(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): Unit =
    sources match {
      case Nil =>
        info(s"Processing ${describeWork(target)}")
      case _ =>
        info(s"Attempting to merge ${describeMergeSet(target, sources)}")
    }

  private def logResult(
    result: MergeResult,
    redirects: Seq[Work[_]],
    remaining: Seq[Work[_]]
  ): Unit = {
    if (redirects.nonEmpty) {
      info(
        s"Merged ${describeMergeOutcome(result.mergedTarget, redirects, remaining)}"
      )
    }
    if (result.imageDataWithSources.nonEmpty) {
      info(s"Created images ${describeImages(result.imageDataWithSources)}")
    }
  }
}

object PlatformMerger extends Merger with WorkMergingOps {
  import weco.catalogue.internal_model.image.ParentWork._

  override def findTarget(
    works: Seq[Work[Identified]]
  ): Option[Work.Visible[Identified]] =
    TargetPrecedence.getTarget(works)

  override def createMergeResult(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): (Seq[Work[Identified]], MergeResult) = {
    val items = ItemsRule(target, sources)
    val thumbnail = ThumbnailRule(target, sources)
    val otherIdentifiers = OtherIdentifiersRule(target, sources)
    val targetImageData = ImageDataRule(target, sources)
    val separateImageData = ImagesRule(target, sources)
    val work = target
      .mapData {
        data =>
          data.copy[DataState.Identified](
            items = items.data,
            thumbnail = thumbnail.data,
            otherIdentifiers = otherIdentifiers.data,
            imageData = targetImageData.data
          )
      }
    val redirectSources = Seq(
      items,
      thumbnail,
      otherIdentifiers,
      targetImageData,
      separateImageData
    ).flatMap(_.sources).distinct
    (
      redirectSources,
      MergeResult(
        mergedTarget = work.withItemsInInternalWorks(items.data),
        imageDataWithSources = separateImageData.data.map {
          imageData =>
            ImageDataWithSource(
              imageData = imageData,
              source = work.toParentWork
            )
        }
      )
    )
  }

}
