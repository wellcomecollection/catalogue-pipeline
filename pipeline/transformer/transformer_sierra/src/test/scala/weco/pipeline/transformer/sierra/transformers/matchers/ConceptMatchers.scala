package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.AbstractRootConcept

trait ConceptMatchers {

  def labelDerivedAbstractConceptId(ontologyType: String, expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] = {
    new SourceIdentifierMatchers.HasLabelDerivedIdentifier(
      ontologyType = ontologyType,
      expectedValue = expectedValue)
  }

  def meshAbstractConceptId(ontologyType: String, expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasMeshIdentifier(
      ontologyType = ontologyType,
      expectedValue = expectedValue)

  def lcSubjectsAbstractConceptId(ontologyType: String, expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLCSubjectsIdentifier(
      ontologyType = ontologyType,
      expectedValue = expectedValue)

  def labelDerivedConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Concept",
      expectedValue = expectedValue)

  def meshConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    meshAbstractConceptId(
      ontologyType = "Concept",
      expectedValue = expectedValue)

  def lcSubjectsConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    lcSubjectsAbstractConceptId(
      ontologyType = "Concept",
      expectedValue = expectedValue)

  def labelDerivedPeriodId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Period",
      expectedValue = expectedValue)

  def meshPeriodId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    meshAbstractConceptId(
      ontologyType = "Period",
      expectedValue = expectedValue)

  def lcSubjectsPeriodId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    lcSubjectsAbstractConceptId(
      ontologyType = "Period",
      expectedValue = expectedValue)

  def labelDerivedPlaceId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    labelDerivedAbstractConceptId(
      ontologyType = "Place",
      expectedValue = expectedValue)
}

object ConceptMatchers extends ConceptMatchers
