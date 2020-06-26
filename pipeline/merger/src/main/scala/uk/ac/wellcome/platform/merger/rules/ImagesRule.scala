package uk.ac.wellcome.platform.merger.rules

import scala.Function.const
import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal.{Identifiable, MergedImage, TransformedBaseWork, UnidentifiedWork, Unminted}
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import uk.ac.wellcome.platform.merger.rules.WorkPredicates.{WorkPredicate, WorkPredicateOps, not}

object ImagesRule extends FieldMergeRule {
  type FieldData = List[MergedImage[Identifiable, Unminted]]

  override def merge(
    target: UnidentifiedWork,
    sources: Seq[TransformedBaseWork] = Nil): FieldMergeResult[FieldData] =
    sources match {
      case Nil =>
        FieldMergeResult(
          data = getSingleMiroImage.applyOrElse(target, const(Nil)),
          sources = Nil)
      case _ :: _ =>
        FieldMergeResult(
          data = getPictureImages(target, sources).getOrElse(Nil) ++
            getPairedMiroImages(target, sources).getOrElse(Nil),
          // The images rule here is the exception where we don't want to redirect works as they have
          // not technically been merged. This rule might change as the rules about merging items
          // loosens up.
          sources = List()
        )
    }

  private lazy val getSingleMiroImage
    : PartialFunction[UnidentifiedWork, FieldData] = {
    case target if WorkPredicates.singleDigitalItemMiroWork(target) =>
      target.data.images.map {
        _.mergeWith(
          target.toSourceWork,
          None
        )
      }
  }


  private lazy val getPictureImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = WorkPredicates.sierraPicture
    val isDefinedForSource
      : WorkPredicate = WorkPredicates.singleDigitalItemMetsWork or WorkPredicates.singleDigitalItemMiroWork
  }

  private lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate =
      WorkPredicates.sierraWork and not(WorkPredicates.sierraPicture)
    val isDefinedForSource: WorkPredicate =
      WorkPredicates.singleDigitalItemMiroWork
  }

  trait FlatImageMergeRule extends PartialRule {
    final override def rule(target: UnidentifiedWork,
                            sources: NonEmptyList[TransformedBaseWork])
      : List[MergedImage[Identifiable, Unminted]] = {
      val works = sources.prepend(target).toList
      works flatMap {
        _.data.images.map {
          _.mergeWith(
            target.toSourceWork,
            Some(sources.head.toSourceWork)
          )
        }
      }
    }
  }

}
