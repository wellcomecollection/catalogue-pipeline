package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.models.work.internal.{IdState, Work}
import uk.ac.wellcome.platform.merger.models.MergerOutcome
import org.scalatest.matchers.{MatchResult, Matcher}
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import weco.catalogue.internal_model.image.ImageData

trait FeatureTestSugar {
  implicit class OutcomeOps(val mergerOutcome: MergerOutcome) {
    def getMerged(originalWork: Work[Identified]): Work[Identified] =
      mergerOutcome.resultWorks
        .find(_.sourceIdentifier == originalWork.sourceIdentifier)
        .get

    def imageSourceIds: Seq[IdState.Identified] =
      mergerOutcome.imagesWithSources.map(_.source.id)

    def imageData: Seq[ImageData[IdState.Identified]] =
      mergerOutcome.imagesWithSources.map(_.imageData)
  }

  implicit class VisibleWorkOps(val work: Work.Visible[Identified]) {
    def singleImage: ImageData[IdState.Identified] =
      work.data.imageData.head
  }

  class RedirectMatcher(expectedRedirectTo: Work.Visible[Identified])
      extends Matcher[Work[Identified]] {
    def apply(left: Work[Identified]): MatchResult = MatchResult(
      left.isInstanceOf[Work.Redirected[Identified]] && left
        .asInstanceOf[Work.Redirected[Identified]]
        .redirectTarget
        .sourceIdentifier == expectedRedirectTo.sourceIdentifier,
      s"${left.sourceIdentifier} was not redirected to ${expectedRedirectTo.sourceIdentifier}",
      s"${left.sourceIdentifier} was redirected to ${expectedRedirectTo.sourceIdentifier}"
    )
  }

  def beRedirectedTo(expectedRedirectTo: Work.Visible[Identified]) =
    new RedirectMatcher(expectedRedirectTo)
}
