package weco.pipeline.merger.services

import cats.data.State
import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.image
import weco.catalogue.internal_model.image.{ImageData, ParentWorks}
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work._
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models
import weco.pipeline.merger.models.{
  FieldMergeResult,
  ImageDataWithSource,
  MergeResult,
  MergerOutcome
}
import weco.pipeline.merger.rules._

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
  ): State[MergeState, MergeResult]

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
        findTarget(matchedWorks).map { target =>
          CategorisedWorks(
            target = target,
            sources = matchedWorks
              .filterNot { _.isInstanceOf[Work.Deleted[Identified]] }
              .filterNot { _.sourceIdentifier == target.sourceIdentifier },
            deleted = matchedWorks.collect {
              case w: Work.Deleted[Identified] => w
            }
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

  def merge(works: Seq[Work[Identified]]): MergerOutcome = {
    categoriseWorks(works)
      .map {
        case CategorisedWorks(target, sources, deleted) =>
          assert((sources ++ deleted :+ target).toSet == works.toSet)

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

          val internalWorks = result.mergedTarget.internalWorksWith(
            thumbnail = result.mergedTarget.data.thumbnail,
            version = result.mergedTarget.version
          )

          val redirectedIdentifiers =
            redirectedSources.map { s =>
              IdState.Identified(s.state.canonicalId, s.sourceIdentifier)
            }.toSeq

          val targetWork: Work.Visible[Identified] =
            Work.Visible[Identified](
              version = result.mergedTarget.version,
              data = result.mergedTarget.data,
              state = result.mergedTarget.state,
              redirectSources = result.mergedTarget.redirectSources ++ redirectedIdentifiers
            )

          MergerOutcome(
            resultWorks = redirects.toList ++ remaining ++ deleted ++ internalWorks :+ targetWork,
            imagesWithSources = result.imageDataWithSources
          )
      }
      .getOrElse(MergerOutcome.passThrough(works))
  }

  private implicit class WorkOps(w: Work[Identified]) {
    def internalWorksWith(thumbnail: Option[DigitalLocation],
                          version: Int): List[Work.Visible[Identified]] =
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

  private def redirectSourceToTarget(
    target: Work.Visible[Identified]
  )(source: Work[Identified]): Work.Redirected[Identified] =
    Work.Redirected[Identified](
      version = source.version,
      state = Identified(
        sourceIdentifier = source.sourceIdentifier,
        canonicalId = source.state.canonicalId,
        sourceModifiedTime = source.state.sourceModifiedTime,
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

object Merger {
  // Parameter can't be `State` as that shadows the Cats type
  implicit class WorkMergingOps[StateT <: WorkState](
    work: Work.Visible[StateT]
  ) {
    def mapData(
      f: WorkData[StateT#WorkDataState] => WorkData[StateT#WorkDataState]
    ): Work.Visible[StateT] =
      work.copy(data = f(work.data))
    def mapState(
      f: StateT => StateT
    ): Work.Visible[StateT] =
      work.copy(state = f(work.state))
  }
}

object PlatformMerger extends Merger {
  import Merger.WorkMergingOps
  import weco.catalogue.internal_model.image.ParentWork._

  override def findTarget(
    works: Seq[Work[Identified]]
  ): Option[Work.Visible[Identified]] =
    TargetPrecedence.getTarget(works)

  override def createMergeResult(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]
  ): State[MergeState, MergeResult] =
    if (sources.isEmpty)
      State.pure(
        models.MergeResult(
          mergedTarget = modifyInternalWorks(target, target.data.items),
          imageDataWithSources = standaloneImages(target).map { image =>
            ImageDataWithSource(
              image,
              ParentWorks(
                canonicalWork = target.toParentWork,
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
        sourceImageData <- ImageDataRule(target, sources).redirectSources
        work = target
          .mapData { data =>
            data.copy[DataState.Identified](
              items = items,
              thumbnail = thumbnail,
              otherIdentifiers = otherIdentifiers,
              imageData = sourceImageData
            )
          }
      } yield
        MergeResult(
          mergedTarget = modifyInternalWorks(work, items),
          imageDataWithSources = sourceImageData.map { imageData =>
            ImageDataWithSource(
              imageData = imageData,
              source = image.ParentWorks(
                canonicalWork = work.toParentWork,
                redirectedWork = sources
                  .find { _.data.imageData.contains(imageData) }
                  .map(_.toParentWork)
              )
            )
          }
        )

  private def modifyInternalWorks(work: Work.Visible[Identified],
                                  items: List[Item[IdState.Minted]]) = {
    // Internal works are in TEI works. If they are merged with Sierra, we want the Sierra
    // items to be added to TEI internal works so that the user can request the item
    // containing that work without having to find the wrapping work.
    work
      .mapState { state =>
        state.copy(internalWorkStubs = state.internalWorkStubs.map { stub =>
          stub.copy(
            workData = stub.workData.copy[DataState.Identified](items = items)
          )
        })
      }
  }

  private def standaloneImages(
    target: Work.Visible[Identified]
  ): List[ImageData[IdState.Identified]] =
    if (WorkPredicates.singleDigitalItemMiroWork(target)) target.data.imageData
    else Nil
}
