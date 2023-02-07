package weco.pipeline.merger.models

import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.Work

/*
 * MergeResult holds the resultant target after all fields have been merged,
 * and the images that were created in the process
 */
case class MergeResult(
  mergedTarget: Work.Visible[Identified],
  imageDataWithSources: Seq[ImageDataWithSource]
)
