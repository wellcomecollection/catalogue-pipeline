package uk.ac.wellcome.platform.merger.services

import cats.data.State
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.rules._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.models.{
  FieldMergeResult,
  ImageWithSource,
  MergeResult,
  MergerOutcome
}
import WorkState.Source

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
    def redirectSources: State[MergeState, T] = shouldRedirect(true)
    def retainSources: State[MergeState, T] = shouldRedirect(false)

    // If the state already contains a source, then don't change the existing `redirect` value
    // Otherwise, add the source with the current value.
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

          val remaining = sources.toSet -- redirectedSources
          val redirects = redirectedSources.map(redirectSourceToTarget(target))
          logResult(result, redirects.toList, remaining.toList)

          MergerOutcome(
            resultWorks = redirects.toList ++ remaining :+ result.mergedTarget,
            imagesWithSources = result.imagesWithSources
          )
      }
      .getOrElse(MergerOutcome.passThrough(works))

  private def redirectSourceToTarget(target: Work.Visible[Source])(
    source: Work[Source]): Work.Redirected[Source] =
    Work.Redirected[Source](
      version = source.version,
      state = Source(source.sourceIdentifier, source.state.modifiedTime),
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
                        redirects: Seq[Work[_]],
                        remaining: Seq[Work[_]]): Unit = {
    if (redirects.nonEmpty) {
      info(
        s"Merged ${describeMergeOutcome(result.mergedTarget, redirects, remaining)}")
    }
    if (result.imagesWithSources.nonEmpty) {
      info(s"Created images ${describeImages(result.imagesWithSources)}")
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
          mergedTarget = target,
          imagesWithSources = standaloneImages(target).map { image =>
            ImageWithSource(
              image,
              SourceWorks(
                canonicalWork = target.toSourceWork,
                redirectedWork = None
              )
            )
          }
        )
      )
    else
      for {
        items <- ItemsRule(target, sources).redirectSources
        thumbnail <- ThumbnailRule(target, sources).redirectSources
        otherIdentifiers <- OtherIdentifiersRule(target, sources).redirectSources
        unmergedImages <- ImagesRule(target, sources).retainSources
        work = target.mapData { data =>
          data.copy[DataState.Unidentified](
            items = items,
            thumbnail = thumbnail,
            otherIdentifiers = otherIdentifiers,
            images = unmergedImages
          )
        }
      } yield
        MergeResult(
          mergedTarget = work,
          imagesWithSources = unmergedImages.map { image =>
            ImageWithSource(
              image = image,
              source = SourceWorks(
                canonicalWork = work.toSourceWork,
                redirectedWork = sources
                  .find { _.data.images.contains(image) }
                  .map(_.toSourceWork)
              )
            )
          }
        )

  private def standaloneImages(
    target: Work.Visible[Source]): List[UnmergedImage[DataState.Unidentified]] =
    if (WorkPredicates.singleDigitalItemMiroWork(target)) target.data.images
    else Nil
}
