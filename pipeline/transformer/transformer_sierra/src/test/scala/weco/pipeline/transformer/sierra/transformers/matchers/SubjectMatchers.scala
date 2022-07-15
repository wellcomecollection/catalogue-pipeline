package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject

trait SubjectMatchers {

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

  def lcSubjectsSubjectId(expectedValue: String)
    : HavePropertyMatcher[Subject[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLCSubjectsIdentifier(
      ontologyType = "Subject",
      expectedValue = expectedValue)
}

object SubjectMatchers extends SubjectMatchers
