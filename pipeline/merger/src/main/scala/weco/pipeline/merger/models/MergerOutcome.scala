package weco.pipeline.merger.models

import java.time.Instant
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.work.Work

/*
 * MergerOutcome creates the final output of the merger:
 * all merged, redirected, and remaining works, as well as all merged images
 * the works/images getters must be provided with a modifiedTime to use for all
 * the output entities.
 */
case class MergerOutcome(
  resultWorks: Seq[Work[Identified]],
  imagesWithSources: Seq[ImageDataWithSource]
) {

  def mergedWorksAndImagesWithTime(
    modifiedTime: Instant
  ): Seq[Either[Work[Merged], Image[Initial]]] =
    mergedWorksWithTime(modifiedTime).map(Left(_)) ++ mergedImagesWithTime(
      modifiedTime
    ).map(Right(_))

  def mergedWorksWithTime(modifiedTime: Instant): Seq[Work[Merged]] =
    resultWorks.map(_.transition[Merged](modifiedTime))

  def mergedImagesWithTime(modifiedTime: Instant): Seq[Image[Initial]] =
    imagesWithSources.map { case ImageDataWithSource(imageData, source) =>
      Image[Initial](
        version = imageData.version,
        locations = imageData.locations,
        source = source,
        modifiedTime = modifiedTime,
        state = Initial(
          sourceIdentifier = imageData.id.sourceIdentifier,
          canonicalId = imageData.id.canonicalId
        )
      )
    }
}

object MergerOutcome {

  def passThrough(works: Seq[Work[Identified]]): MergerOutcome =
    new MergerOutcome(works, Nil)
}
