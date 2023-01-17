package weco.pipeline.merger.rules

import cats.data.NonEmptyList
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.models.FieldMergeResult

object ImageDataRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[ImageData[IdState.Identified]]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]] = Nil
  ): FieldMergeResult[FieldData] = {
    // We merge images into Sierra targets, regardless of whether this is the principal
    // target of the graph we're currently merging (ie if there's a Calm target, it's ignored)
    TargetPrecedence
      .targetSatisfying(sierraWork)(
        target +: sources.collect(TargetPrecedence.visibleWork)
      )
      .map(mergeSierraImages(sources))
      .getOrElse(FieldMergeResult(data = Nil, sources = Nil))
  }

  private def mergeSierraImages(
    sources: Seq[Work[Identified]]
  )(sierraTarget: Work.Visible[Identified]) =
    FieldMergeResult(
      data = getMetsPictureAndEphemeraImages(sierraTarget, sources)
        .getOrElse(Nil) ++
        getPairedMiroImages(sierraTarget, sources).getOrElse(Nil),
      sources = List(
        getMetsPictureAndEphemeraImages,
        getPairedMiroImages
      ).flatMap(_.mergedSources(sierraTarget, sources))
    )

  private lazy val getMetsPictureAndEphemeraImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraPictureOrEphemera
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork
  }

  private lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate =
      sierraWork and not(sierraDigitisedMiro)
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  trait FlatImageMergeRule extends PartialRule {
    final override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): List[ImageData[IdState.Identified]] =
      (target :: sources).toList.flatMap(_.data.imageData)
  }
}
