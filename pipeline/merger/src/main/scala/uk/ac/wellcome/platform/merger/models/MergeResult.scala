package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  Identifiable,
  MergedImage,
  UnidentifiedWork,
  Unminted
}

/*
 * MergeResult holds the resultant target after all fields have been merged,
 * and the images that were created in the process
 */
case class MergeResult(mergedTarget: UnidentifiedWork,
                       images: Seq[MergedImage[Identifiable, Unminted]])
