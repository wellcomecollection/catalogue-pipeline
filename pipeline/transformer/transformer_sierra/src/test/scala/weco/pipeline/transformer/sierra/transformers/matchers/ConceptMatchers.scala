package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.{HasId, IdentifierType}

trait ConceptMatchers {

  def labelDerivedAbstractConceptId(
    ontologyType: String,
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] = {
    new HasIdMatchers.HasIdentifier(
      identifierType = IdentifierType.LabelDerived,
      ontologyType = ontologyType,
      value = expectedValue
    )
  }

  def labelDerivedConceptId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Concept",
      expectedValue = expectedValue
    )

  def labelDerivedGenreId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Genre",
      expectedValue = expectedValue
    )

  def labelDerivedPeriodId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Period",
      expectedValue = expectedValue
    )

  def labelDerivedPlaceId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Place",
      expectedValue = expectedValue
    )

  def labelDerivedMeetingId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Meeting",
      expectedValue = expectedValue
    )

  def labelDerivedPersonId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Person",
      expectedValue = expectedValue
    )

  def labelDerivedOrganisationId(
    expectedValue: String
  ): HavePropertyMatcher[HasId[Any], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Organisation",
      expectedValue = expectedValue
    )

}
