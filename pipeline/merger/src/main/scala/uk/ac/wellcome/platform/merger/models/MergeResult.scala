package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._

/*
 * MergeResult holds the resultant target after all fields have been merged,
 * and the images that were created in the process
 */
case class MergeResult(mergedTarget: Work[Identified],
                       imageDataWithSources: Seq[ImageDataWithSource])
