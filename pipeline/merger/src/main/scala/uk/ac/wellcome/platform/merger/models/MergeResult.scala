package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal._
import WorkState.Unidentified

/*
 * MergeResult holds the resultant target after all fields have been merged,
 * and the images that were created in the process
 */
case class MergeResult(
  mergedTarget: Work[Unidentified],
  images: Seq[MergedImage[DataState.Unidentified]])
