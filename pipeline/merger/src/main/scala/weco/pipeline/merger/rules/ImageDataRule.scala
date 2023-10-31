package weco.pipeline.merger.rules

import cats.data.NonEmptyList
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.models.{FieldMergeResult, ImageDataOps}
/*
 * Rule for populating the imageData property on the merged target
 * from the itself and its sources.
 *
 * The imageData property is populated from the
 * */

trait ImageRule extends FieldMergeRule {

  import WorkPredicates._

  type FieldData = List[ImageData[IdState.Identified]]

  protected def mergeSierraImages(
    sources: Seq[Work[Identified]]
  )(sierraTarget: Work.Visible[Identified]): FieldMergeResult[FieldData]

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

  protected lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate =
      sierraWork and not(sierraDigitisedMiro)
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  protected lazy val getMetsPictureAndEphemeraImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = sierraPictureOrEphemera
    val isDefinedForSource: WorkPredicate = singleDigitalItemMetsWork
  }

  trait FlatImageMergeRule extends PartialRule {
    /*
     * Merge all the imageData for the target and sources.
     */
    final override def rule(
      target: Work.Visible[Identified],
      sources: NonEmptyList[Work[Identified]]
    ): List[ImageData[IdState.Identified]] =
      (target :: sources).toList.flatMap(_.data.imageData)
  }
}

object ImageDataRule extends ImageRule {

  override def mergeSierraImages(
    sources: Seq[Work[Identified]]
  )(sierraTarget: Work.Visible[Identified]): FieldMergeResult[FieldData] = {

    val miroImages = getPairedMiroImages(sierraTarget, sources).getOrElse(Nil)

    FieldMergeResult(
      // Only Miro images contribute to the imageData list in the target Work,
      // Mets images are covered by being included in the items list.
      data = miroImages,
      sources = List(
        getPairedMiroImages
      ).flatMap(_.mergedSources(sierraTarget, sources))
    )
  }
}

object ImagesRule extends ImageRule with ImageDataOps {
  private def mergeMetsLicenceIntoMiroLocation(
    miroImageData: FieldData,
    metsImageData: FieldData
  ): FieldData = {
    miroImageData.map(_.copyLicenceFrom(metsImageData))
  }

  override def mergeSierraImages(
    sources: Seq[Work[Identified]]
  )(sierraTarget: Work.Visible[Identified]): FieldMergeResult[FieldData] = {
    val metsImages =
      getMetsPictureAndEphemeraImages(sierraTarget, sources).getOrElse(Nil)
    val miroImages = mergeMetsLicenceIntoMiroLocation(
      getPairedMiroImages(sierraTarget, sources).getOrElse(Nil),
      metsImages
    )

    FieldMergeResult(
      data = metsImages ++ miroImages,
      sources = List(
        getMetsPictureAndEphemeraImages,
        getPairedMiroImages
      ).flatMap(_.mergedSources(sierraTarget, sources))
    )
  }
}
