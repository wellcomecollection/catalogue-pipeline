package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal._
import WorkState.{Source, Merged}
import WorkFsm.TransitionSourceWork

/*
 * MergerOutcome is the final output of the merger:
 * all merged, redirected, and remaining works, as well as all merged images
 */
case class MergerOutcome(works: Seq[Work[Merged]],
                         images: Seq[MergedImage[DataState.Unidentified]])

object MergerOutcome {

  def passThrough(works: Seq[Work[Source]]): MergerOutcome =
    MergerOutcome(
      works = works.map(_.transitionToMerged(isMerged = false)),
      images = Nil
    )
}
