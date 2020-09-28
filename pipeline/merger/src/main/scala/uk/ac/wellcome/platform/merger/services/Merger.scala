package uk.ac.wellcome.platform.merger.services

import cats.data.State
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.rules._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}
import WorkState.{Merged, Source}
import WorkFsm._

/*
 * The implementor of a Merger must provide:
 * - `findTarget`, which finds the target from the input works
 * - `createMergeResult`, a recipe for creating a merged target and a complete
 *    list of works that should be redirected as a result of any merged fields.
 *
 * Calling `merge` with a list of works will return a new list of works including:
 * - the target work with all fields merged
 * - all redirected sources
 * - any other works untouched
 */
trait Merger extends MergerLogging {
  type MergeState = State[Set[Work[Source]], MergeResult]

  protected def findTarget(
    works: Seq[Work[Source]]): Option[Work.Visible[Source]]

  protected def createMergeResult(target: Work.Visible[Source],
                                  sources: Seq[Work[Source]]): MergeState

  protected def getTargetAndSources(works: Seq[Work[Source]])
    : Option[(Work.Visible[Source], Seq[Work[Source]])] =
    works match {
      case List(unmatchedWork: Work.Visible[Source]) =>
        Some((unmatchedWork, Nil))
      case matchedWorks =>
        findTarget(matchedWorks).map { target =>
          (
            target,
            matchedWorks.filterNot(
              _.sourceIdentifier == target.sourceIdentifier
            )
          )
        }
    }

  def merge(works: Seq[Work[Source]]): MergerOutcome =
    getTargetAndSources(works)
      .map {
        case (target, sources) =>
          logIntentions(target, sources)
          val (mergeResultSources, result) = createMergeResult(target, sources)
            .run(Set.empty)
            .value

          val remaining = (sources.toSet -- mergeResultSources)
            .map(_.transitionToMerged(isMerged = false))
          val redirects = mergeResultSources.map(redirectSourceToTarget(target))
            .map(_.transitionToMerged(isMerged = false))
          logResult(result, redirects.toList, remaining.toList)

          MergerOutcome(
            works = redirects.toList ++ remaining :+ result.mergedTarget,
            images = result.images
          )
      }
      .getOrElse(MergerOutcome.passThrough(works))

  private def redirectSourceToTarget(target: Work.Visible[Source])(
    source: Work[Source]): Work.Redirected[Source] =
    Work.Redirected[Source](
      version = source.version,
      state = Source(source.sourceIdentifier),
      redirect = IdState.Identifiable(target.sourceIdentifier)
    )

  private def logIntentions(target: Work.Visible[Source],
                            sources: Seq[Work[Source]]): Unit =
    sources match {
      case Nil =>
        info(s"Processing ${describeWork(target)}")
      case _ =>
        info(s"Attempting to merge ${describeMergeSet(target, sources)}")
    }

  private def logResult(result: MergeResult,
                        redirects: Seq[Work[Merged]],
                        remaining: Seq[Work[Merged]]): Unit = {
    if (redirects.nonEmpty) {
      info(
        s"Merged ${describeMergeOutcome(result.mergedTarget, redirects, remaining)}")
    }
    if (result.images.nonEmpty) {
      info(s"Created images ${describeImages(result.images)}")
    }
  }
}

object PlatformMerger extends Merger {
  override def findTarget(
    works: Seq[Work[Source]]): Option[Work.Visible[Source]] =
    works
      .find(WorkPredicates.singlePhysicalItemCalmWork)
      .orElse(works.find(WorkPredicates.physicalSierra))
      .orElse(works.find(WorkPredicates.sierraWork)) match {
      case Some(target: Work.Visible[Source]) => Some(target)
      case _                                        => None
    }

  override def createMergeResult(target: Work.Visible[Source],
                                 sources: Seq[Work[Source]]): MergeState =
    if (sources.isEmpty)
      State.pure(
        MergeResult(
          mergedTarget = target.transitionToMerged(isMerged = false),
          images = ImagesRule.merge(target).data
        )
      )
    else
      for {
        items <- ItemsRule(target, sources)
        thumbnail <- ThumbnailRule(target, sources)
        otherIdentifiers <- OtherIdentifiersRule(target, sources)
        images <- ImagesRule(target, sources)
        work = target.withData { data =>
          data.copy[DataState.Unidentified](
            items = items,
            thumbnail = thumbnail,
            otherIdentifiers = otherIdentifiers,
            images = images.map(_.toUnmerged)
          )
        }
      } yield MergeResult(
        mergedTarget = work.transitionToMerged(isMerged = true),
        images = images
      )
}
