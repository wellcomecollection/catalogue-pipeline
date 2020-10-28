package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.models.work.internal.{
  DataState,
  IdState,
  UnmergedImage,
  Work,
  WorkState
}
import uk.ac.wellcome.platform.merger.models.MergerOutcome
import WorkState._
import IdState._
import org.scalatest.matchers.{MatchResult, Matcher}

trait FeatureTestSugar {
  implicit class OutcomeOps(val mergerOutcome: MergerOutcome) {
    def getMerged(originalWork: Work.Visible[Source]): Work[Source] =
      mergerOutcome.resultWorks
        .find(_.sourceIdentifier == originalWork.sourceIdentifier)
        .get

    def imageSourceIds: Seq[Identifiable] =
      mergerOutcome.imagesWithSources.map(_.source.id)

    def images: Seq[UnmergedImage[DataState.Unidentified]] =
      mergerOutcome.imagesWithSources.map(_.image)
  }

  implicit class VisibleWorkOps(val work: Work.Visible[Source]) {
    def singleImage: UnmergedImage[DataState.Unidentified] =
      work.data.images.head
  }

  class RedirectMatcher(expectedRedirectTo: Work.Visible[Source])
      extends Matcher[Work[Source]] {
    def apply(left: Work[Source]): MatchResult = MatchResult(
      left.isInstanceOf[Work.Redirected[Source]] && left
        .asInstanceOf[Work.Redirected[Source]]
        .redirect
        .sourceIdentifier == expectedRedirectTo.sourceIdentifier,
      s"${left.sourceIdentifier} was not redirected to ${expectedRedirectTo.sourceIdentifier}",
      s"${left.sourceIdentifier} was redirected to ${expectedRedirectTo.sourceIdentifier}"
    )
  }

  def beRedirectedTo(expectedRedirectTo: Work.Visible[Source]) =
    new RedirectMatcher(expectedRedirectTo)
}
