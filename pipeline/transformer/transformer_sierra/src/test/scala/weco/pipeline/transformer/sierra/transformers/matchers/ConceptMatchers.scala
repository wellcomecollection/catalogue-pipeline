package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.HavePropertyMatcher
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.AbstractRootConcept

trait ConceptMatchers {

  def labelDerivedConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLabelDerivedIdentifier(
      ontologyType = "Concept",
      expectedValue = expectedValue)

  def meshConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasMeshIdentifier(
      ontologyType = "Concept",
      expectedValue = expectedValue)

  def lcSubjectsConceptId(expectedValue: String)
    : HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLCSubjectsIdentifier(
      ontologyType = "Concept",
      expectedValue = expectedValue)
}

object ConceptMatchers extends ConceptMatchers
