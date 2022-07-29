package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.AbstractRootConcept

trait ConceptMatchers {

  def labelDerivedAbstractConceptId(ontologyType: String, expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[Any], String] = {
    new HasIdMatchers.HasIdentifier(
      identifierType = IdentifierType.LabelDerived,
      ontologyType = ontologyType,
      value = expectedValue)
  }

  def labelDerivedConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Concept",
      expectedValue = expectedValue)

  def labelDerivedPeriodId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Period",
      expectedValue = expectedValue)

  def labelDerivedPlaceId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Place",
      expectedValue = expectedValue)

  def labelDerivedMeetingId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Meeting",
      expectedValue = expectedValue)

}
