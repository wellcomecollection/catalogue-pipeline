package uk.ac.wellcome.platform.merger.models

import java.time.Instant

import uk.ac.wellcome.models.work.internal._
import WorkState.{Merged, Source}
import WorkFsm._

/*
 * MergerOutcome is the final output of the merger:
 * all merged, redirected, and remaining works, as well as all merged images
 */
case class MergerOutcome(works: Seq[Work[Merged]],
                         images: Seq[MergedImage[DataState.Unidentified]]) {
  def lastUpdated(lastUpdated: Instant): MergerOutcome = MergerOutcome(
    works = works.map(touchWorkLastUpdated(lastUpdated)),
    images = images.map(touchImageLastUpdated(lastUpdated)),
  )

  private def touchImageLastUpdated(lastUpdated: Instant)(
    image: MergedImage[DataState.Unidentified])
    : MergedImage[DataState.Unidentified] = ???
  private def touchWorkLastUpdated(lastUpdated: Instant)(
    work: Work[WorkState.Merged]): Work[WorkState.Merged] = ???
}

object MergerOutcome {

  def passThrough(works: Seq[Work[Source]]): MergerOutcome =
    MergerOutcome(
      works = works.map(_.transition[Merged](1)),
      images = Nil
    )
}
