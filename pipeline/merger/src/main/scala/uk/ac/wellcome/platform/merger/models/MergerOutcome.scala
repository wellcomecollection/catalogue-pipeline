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
  def withModifiedTime(modifiedTime: Instant): MergerOutcome = MergerOutcome(
    works = works.map(workWithModifiedTime(modifiedTime)),
    images = images.map(imageWithModifiedTime(modifiedTime)),
  )

  private def imageWithModifiedTime(modifiedTime: Instant)(
    image: MergedImage[DataState.Unidentified])
    : MergedImage[DataState.Unidentified] = image.copy(
    modifiedTime = modifiedTime
  )

  private def workWithModifiedTime(modifiedTime: Instant)(
    work: Work[Merged]): Work[Merged] = work match {
    case visibleWork @ Work.Visible(_, _, state) =>
      visibleWork.copy(state = state.copy(modifiedTime = modifiedTime))
    case redirectedWork @ Work.Redirected(_, _, state) =>
      redirectedWork.copy(state = state.copy(modifiedTime = modifiedTime))
    case invisibleWork @ Work.Invisible(_, _, state, _) =>
      invisibleWork.copy(state = state.copy(modifiedTime = modifiedTime))
  }
}

object MergerOutcome {

  def passThrough(works: Seq[Work[Source]]): MergerOutcome =
    MergerOutcome(
      works = works.map(_.transition[Merged]),
      images = Nil
    )
}
