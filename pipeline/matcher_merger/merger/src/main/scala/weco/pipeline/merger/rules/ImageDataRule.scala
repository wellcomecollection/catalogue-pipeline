package weco.pipeline.merger.rules

import cats.data.NonEmptyList
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.models.{FieldMergeResult, ImageDataOps}
import weco.pipeline.merger.rules.WorkPredicates.{
  lmsWork,
  lmsWorkDigitisedMiro,
  not,
  WorkPredicate
}
/*
 * Rule for populating the imageData property on the merged target
 * from itself and its sources.
 */

trait ImageRule extends FieldMergeRule {
  import WorkPredicates._

  type FieldData = List[ImageData[IdState.Identified]]

  protected def mergeLmsWorkImages(
    sources: Seq[Work[Identified]]
  )(lmsWorkTarget: Work.Visible[Identified]): FieldMergeResult[FieldData]

  override def merge(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]] = Nil
  ): FieldMergeResult[FieldData] = {
    // We merge images into LMS targets, regardless of whether this is the principal
    // target of the graph we're currently merging.
    // This is because the LMS participant is the one that
    // contains the best information to enable us to choose
    // which images to put where.
    // The resulting FieldMergeResult is still expected to be applied to the
    // actual target.
    val allParticipants =
      target +: sources.collect(TargetPrecedence.visibleWork)
    val maybeLmsWorkTarget: Option[Work.Visible[Identified]] =
      TargetPrecedence
        .targetSatisfying(lmsWorkDigitisedMiro)(allParticipants)
        .orElse(TargetPrecedence.targetSatisfying(lmsWork)(allParticipants))

    maybeLmsWorkTarget
      .map(mergeLmsWorkImages(sources))
      .getOrElse(FieldMergeResult(data = Nil, sources = Nil))
  }

  protected lazy val getPairedMiroImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate =
      lmsWork and not(lmsWorkDigitisedMiro)
    val isDefinedForSource: WorkPredicate = singleDigitalItemMiroWork
  }

  protected lazy val getMetsPictureAndEphemeraImages = new FlatImageMergeRule {
    val isDefinedForTarget: WorkPredicate = lmsWorkPictureOrEphemera
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

  override def mergeLmsWorkImages(
    sources: Seq[Work[Identified]]
  )(lmsWorkTarget: Work.Visible[Identified]): FieldMergeResult[FieldData] = {

    val miroImages = getPairedMiroImages(lmsWorkTarget, sources).getOrElse(Nil)

    FieldMergeResult(
      // Only Miro images contribute to the imageData list in the target Work,
      // Mets images are covered by being included in the items list.
      data = miroImages,
      sources = List(
        getPairedMiroImages
      ).flatMap(_.mergedSources(lmsWorkTarget, sources))
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

  override def mergeLmsWorkImages(
    sources: Seq[Work[Identified]]
  )(lmsWorkTarget: Work.Visible[Identified]): FieldMergeResult[FieldData] = {
    val metsImages =
      getMetsPictureAndEphemeraImages(lmsWorkTarget, sources).getOrElse(Nil)
    val miroImages = mergeMetsLicenceIntoMiroLocation(
      getPairedMiroImages(lmsWorkTarget, sources).getOrElse(Nil),
      metsImages
    )

    FieldMergeResult(
      data = metsImages ++ miroImages,
      sources = List(
        getMetsPictureAndEphemeraImages,
        getPairedMiroImages
      ).flatMap(_.mergedSources(lmsWorkTarget, sources))
    )
  }
}
