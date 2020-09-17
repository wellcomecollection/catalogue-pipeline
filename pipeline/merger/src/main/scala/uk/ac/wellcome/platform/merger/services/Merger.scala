package uk.ac.wellcome.platform.merger.services

import cats.data.State
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.rules._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.{MergeResult, MergerOutcome}
import WorkState.Unidentified

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
  type MergeState = State[Set[Work[Unidentified]], MergeResult]

  protected def findTarget(
    works: Seq[Work[Unidentified]]): Option[Work.Visible[Unidentified]]

  protected def createMergeResult(target: Work.Visible[Unidentified],
                                  sources: Seq[Work[Unidentified]]): MergeState

  protected def getTargetAndSources(works: Seq[Work[Unidentified]])
    : Option[(Work.Visible[Unidentified], Seq[Work[Unidentified]])] =
    works match {
      case List(unmatchedWork: Work.Visible[Unidentified]) =>
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

  def merge(works: Seq[Work[Unidentified]]): MergerOutcome =
    getTargetAndSources(works)
      .map {
        case (target, sources) =>
          logIntentions(target, sources)
          val (mergeResultSources, result) = createMergeResult(target, sources)
            .run(Set.empty)
            .value

          val remaining = sources.toSet -- mergeResultSources
          val redirects = mergeResultSources.map(redirectSourceToTarget(target))
          logResult(result, redirects.toList, remaining.toList)

          MergerOutcome(
            works = redirects.toList ++ remaining :+ result.mergedTarget,
            images = result.images
          )
      }
      .getOrElse(MergerOutcome(works, Nil))

  private def redirectSourceToTarget(target: Work.Visible[Unidentified])(
    source: Work[Unidentified]): Work.Redirected[Unidentified] =
    Work.Redirected[Unidentified](
      version = source.version,
      state = Unidentified(source.sourceIdentifier),
      redirect = IdState.Identifiable(target.sourceIdentifier)
    )

  private def logIntentions(target: Work.Visible[Unidentified],
                            sources: Seq[Work[Unidentified]]): Unit =
    sources match {
      case Nil =>
        info(s"Processing ${describeWork(target)}")
      case _ =>
        info(s"Attempting to merge ${describeMergeSet(target, sources)}")
    }

  private def logResult(result: MergeResult,
                        redirects: Seq[Work.Redirected[Unidentified]],
                        remaining: Seq[Work[Unidentified]]): Unit = {
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
    works: Seq[Work[Unidentified]]): Option[Work.Visible[Unidentified]] =
    works
      .find(WorkPredicates.singlePhysicalItemCalmWork)
      .orElse(works.find(WorkPredicates.physicalSierra))
      .orElse(works.find(WorkPredicates.sierraWork)) match {
      case Some(target: Work.Visible[Unidentified]) => Some(target)
      case _                                         => None
    }

  override def createMergeResult(target: Work.Visible[Unidentified],
                                 sources: Seq[Work[Unidentified]]): MergeState =
    if (sources.isEmpty)
      State.pure(
        MergeResult(
          mergedTarget = target,
          images = ImagesRule.merge(target).data
        )
      )
    else
      for {
        items <- ItemsRule(target, sources)
        thumbnail <- ThumbnailRule(target, sources)
        otherIdentifiers <- OtherIdentifiersRule(target, sources)
        images <- ImagesRule(target, sources)
      } yield
        MergeResult(
          mergedTarget = target.withData { data =>
            data.copy[DataState.Unidentified](
              merged = true,
              items = items,
              thumbnail = thumbnail,
              otherIdentifiers = otherIdentifiers,
              images = images.map(_.toUnmerged)
            )
          },
          images = images
        )
}
