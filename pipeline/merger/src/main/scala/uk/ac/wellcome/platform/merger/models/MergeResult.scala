package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  IdState,
  MergedImage,
  TransformedBaseWork,
}

/*
 * MergeResult holds the resultant target after all fields have been merged,
 * and the images that were created in the process
 */
case class MergeResult(mergedTarget: TransformedBaseWork,
                       images: Seq[MergedImage[IdState.Identifiable, IdState.Unminted]])
