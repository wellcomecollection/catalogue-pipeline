package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject

trait SubjectMatchers {

  private class HasLabel(expectedValue: String)
      extends HavePropertyMatcher[Subject[IdState.Unminted], String] {
    override def apply(
      subject: Subject[IdState.Unminted]): HavePropertyMatchResult[String] = {
      HavePropertyMatchResult(
        subject.label == expectedValue,
        "label",
        expectedValue,
        subject.label
      )
    }
  }

  def subjectLabel(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new HasLabel(expectedValue)

  def labelDerivedSubjectId(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLabelDerivedIdentifier(
      ontologyType = "Subject",
      expectedValue = expectedValue)

  def meshSubjectId(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasMeshIdentifier(
      ontologyType = "Subject",
      expectedValue = expectedValue)
}
