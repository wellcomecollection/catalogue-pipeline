package uk.ac.wellcome.platform.merger.rules

import cats.data.NonEmptyList

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.models.FieldMergeResult
import WorkState.Source

object ImagesRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[UnmergedImage[DataState.Unidentified]]

  override def merge(
    target: Work.Visible[Source],
    sources: Seq[Work[Source]] = Nil): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = getPictureAndEphemeraImages(target, sources).getOrElse(Nil) ++
        getPairedMiroImages(target, sources).getOrElse(Nil),
      sources = List(getPictureAndEphemeraImages, getPairedMiroImages)
        .flatMap(_.mergedSources(target, sources))
    )

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
      : List[UnmergedImage[DataState.Unidentified]] =
      (target :: sources).toList.flatMap(_.data.images)
  }

}
