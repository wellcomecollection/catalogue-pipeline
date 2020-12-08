package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import WorkState.Source

object ImageDataRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[ImageData[IdState.Identifiable]]

  override def merge(
    target: Work.Visible[Source],
    sources: Seq[Work[Source]] = Nil): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = getOnlyMetsDigaidsImages(target, sources).getOrElse(
        getPictureAndEphemeraImages(target, sources).getOrElse(Nil) ++
          getPairedMiroImages(target, sources).getOrElse(Nil)
      ),
      sources = getOnlyMetsDigaidsImages.mergedSources(target, sources) match {
        case Nil =>
          List(getPictureAndEphemeraImages, getPairedMiroImages)
            .flatMap(_.mergedSources(target, sources))
        case digaidsMets => digaidsMets
      }
    )

  // In future this may be changed to `digmiro` for all works
  // where we know that the Miro and METS images are identical
  private lazy val getOnlyMetsDigaidsImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate =
      sierraDigaids and sierraPictureOrEphemera
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork
  }

  private lazy val getPictureAndEphemeraImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraPictureOrEphemera
    val isDefinedForSource
      : WorkPredicate = singleDigitalItemMetsWork or singleDigitalItemMiroWork
  }

  private lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraWork and not(
      sierraPictureOrEphemera)
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  trait FlatImageMergeRule extends PartialRule {
    final override def rule(target: Work.Visible[Source],
                            sources: NonEmptyList[Work[Source]])
      : List[ImageData[IdState.Identifiable]] =
      (target :: sources).toList.flatMap(_.data.imageData)
  }

}
