package uk.ac.wellcome.platform.merger.rules

import scala.Function.const
import cats.data.NonEmptyList

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import WorkState.Unidentified
import SourceWork._

object ImagesRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[MergedImage[IdState.Identifiable, WorkState.Unidentified]]

  override def merge(
    target: Work.Standard[Unidentified],
    sources: Seq[Work[Unidentified]] = Nil): FieldMergeResult[FieldData] =
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
    : PartialFunction[Work.Standard[Unidentified], FieldData] = {
    case target if singleDigitalItemMiroWork(target) =>
      target.data.images.map {
        _.mergeWith(
          target.toSourceWork,
          None
        )
      }
  }

  private lazy val getPictureImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraPicture
    val isDefinedForSource
      : WorkPredicate = singleDigitalItemMetsWork or singleDigitalItemMiroWork
  }

  private lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraWork and not(sierraPicture)
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  trait FlatImageMergeRule extends PartialRule {
    final override def rule(target: Work.Standard[Unidentified],
                            sources: NonEmptyList[Work[Unidentified]])
      : List[MergedImage[IdState.Identifiable, WorkState.Unidentified]] = {
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
