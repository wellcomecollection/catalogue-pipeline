package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  MergedImage,
  Id
}

/*
 * MergerOutcome is the final output of the merger:
 * all merged, redirected, and remaining works, as well as all merged images
 */
case class MergerOutcome(works: Seq[BaseWork],
                         images: Seq[MergedImage[Id.Identifiable, Id.Unminted]])
