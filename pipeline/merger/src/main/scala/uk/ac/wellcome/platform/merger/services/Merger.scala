package uk.ac.wellcome.platform.merger.services

import cats.data.State
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.rules._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.{
  FieldMergeResult,
  MergeResult,
  MergerOutcome
}
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
  type MergeState = Map[Work[Source], Boolean]

  protected def findTarget(
    works: Seq[Work[Source]]): Option[Work.Visible[Source]]

  protected def createMergeResult(
    target: Work.Visible[Source],
    sources: Seq[Work[Source]]): State[MergeState, MergeResult]

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

  implicit class MergeResultAccumulation[T](val result: FieldMergeResult[T]) {
    def redirect: State[MergeState, T] = shouldRedirect(true)
    def noRedirect: State[MergeState, T] = shouldRedirect(false)

    private def shouldRedirect(redirect: Boolean): State[MergeState, T] =
      State { prevState =>
        val nextState = result.sources.foldLeft(prevState) {
          case (state, source) if state.contains(source) => state
          case (state, source)                           => state + (source -> redirect)
        }
        (nextState, result.data)
      }
  }

  def merge(works: Seq[Work[Source]]): MergerOutcome =
    getTargetAndSources(works)
      .map {
        case (target, sources) =>
          logIntentions(target, sources)
          val (mergeResultSources, result) = createMergeResult(target, sources)
            .run(Map.empty)
            .value
          val redirectedSources = mergeResultSources.collect {
            case (source, true) => source
          }

          val remaining = (sources.toSet -- redirectedSources)
            .map(_.transition[Merged](0))
          val redirects = redirectedSources
            .map(redirectSourceToTarget(target))
            .map(_.transition[Merged](0))
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
  import SourceWork._

  override def findTarget(
    works: Seq[Work[Source]]): Option[Work.Visible[Source]] =
    works
      .find(WorkPredicates.singlePhysicalItemCalmWork)
      .orElse(works.find(WorkPredicates.physicalSierra))
      .orElse(works.find(WorkPredicates.sierraWork)) match {
      case Some(target: Work.Visible[Source]) => Some(target)
      case _                                  => None
    }

  override def createMergeResult(
    target: Work.Visible[Source],
    sources: Seq[Work[Source]]): State[MergeState, MergeResult] =
    if (sources.isEmpty)
      State.pure(
        MergeResult(
          mergedTarget = target.transition[Merged](0),
          images = standaloneImages(target).map {
            _ mergeWith (
              canonicalWork = target.toSourceWork,
              redirectedWork = None,
              nMergedSources = 0
            )
          }
        )
      )
    else
      for {
        items <- ItemsRule(target, sources).redirect
        thumbnail <- ThumbnailRule(target, sources).redirect
        otherIdentifiers <- OtherIdentifiersRule(target, sources).redirect
        unmergedImages <- ImagesRule(target, sources).noRedirect
        work = target.withData { data =>
          data.copy[DataState.Unidentified](
            items = items,
            thumbnail = thumbnail,
            otherIdentifiers = otherIdentifiers,
            images = unmergedImages
          )
        }
        nSources <- State.inspect[MergeState, Int](_.size)
      } yield
        MergeResult(
          mergedTarget = work.transition[Merged](nSources),
          images = unmergedImages.map { image =>
            image mergeWith (
              canonicalWork = work.toSourceWork,
              redirectedWork = sources
                .find { _.data.images.contains(image) }
                .map(_.toSourceWork),
              nMergedSources = nSources
            )
          }
        )

  private def standaloneImages(
    target: Work.Visible[Source]): List[UnmergedImage[DataState.Unidentified]] =
    if (WorkPredicates.singleDigitalItemMiroWork(target)) target.data.images
    else Nil
}
