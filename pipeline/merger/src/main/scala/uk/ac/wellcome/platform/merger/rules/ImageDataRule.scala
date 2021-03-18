package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.Work

object ImageDataRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[ImageData[IdState.Identified]]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]] = Nil): FieldMergeResult[FieldData] =
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
    final override def rule(target: Work.Visible[Identified],
                            sources: NonEmptyList[Work[Identified]])
      : List[ImageData[IdState.Identified]] =
      (target :: sources).toList.flatMap(_.data.imageData)
  }

}
