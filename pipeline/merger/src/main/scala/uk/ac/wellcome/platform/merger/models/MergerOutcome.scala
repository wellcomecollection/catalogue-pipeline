package uk.ac.wellcome.platform.merger.models

import java.time.Instant

import uk.ac.wellcome.models.work.internal._
import WorkState.{Merged, Source}
import WorkFsm._

/*
 * MergerOutcome creates the final output of the merger:
 * all merged, redirected, and remaining works, as well as all merged images
 * the works/images getters must be provided with a modifiedTime to use for all
 * the output entities.
 */
class MergerOutcome(resultWorks: Seq[Work[Source]],
                    imagesWithSources: Seq[ImageWithSource]) {

  // numberOfSources is hardcoded here so as not to break builds
  // TODO: remove numberOfSources
  def mergedWorksWithTime(modifiedTime: Instant): Seq[Work[Merged]] =
    resultWorks.map(_.transition[Merged]((Some(modifiedTime), 1)))

  def mergedImagesWithTime(
    modifiedTime: Instant): Seq[MergedImage[DataState.Unidentified]] =
    imagesWithSources.map {
      case ImageWithSource(image, source) =>
        image.mergeWith(source, modifiedTime)
    }
}

object MergerOutcome {

  def passThrough(works: Seq[Work[Source]]): MergerOutcome =
    new MergerOutcome(works, Nil)
}
