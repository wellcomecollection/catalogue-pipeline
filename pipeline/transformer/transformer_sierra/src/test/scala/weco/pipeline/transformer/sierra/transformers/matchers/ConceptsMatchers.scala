package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.AbstractRootConcept

trait ConceptsMatchers {
  private class HasLabel(expectedValue: String) extends HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] {
    override def apply(concept: AbstractRootConcept[IdState.Unminted]): HavePropertyMatchResult[String] = {
      HavePropertyMatchResult(
        concept.label == expectedValue,
        "label",
        expectedValue,
        concept.label
      )
    }
  }

  def conceptLabel(expectedValue: String): HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new HasLabel(expectedValue)

  def labelDerivedConceptId(expectedValue: String): HavePropertyMatcher[AbstractRootConcept[IdState.Unminted], String] =
    new SourceIdentifierMatchers.HasLabelDerivedIdentifier(ontologyType = "Concept", expectedValue = expectedValue)
}

object ConceptsMatchers extends ConceptsMatchers
