package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.identifiers.{
  HasId,
  IdState,
  IdentifierType,
  SourceIdentifier
}

trait HasIdMatchers {

  class HasIdentifier(ontologyType: String,
                      expectedValue: String,
                      identifierType: IdentifierType)
      extends HavePropertyMatcher[HasId[IdState], String] {
    def apply(
      identifiableObject: HasId[IdState]): HavePropertyMatchResult[String] = {
      identifiableObject.id match {
        case identified: IdState.Identifiable =>
          matchIdentifiableId(identified.sourceIdentifier)
        case _ =>
          HavePropertyMatchResult[String](
            matches = false,
            propertyName = "id",
            expectedValue = IdState.Identifiable.toString,
            actualValue = identifiableObject.id.toString
          )
      }
    }

    protected def matchIdentifiableId(
      sourceIdentifier: SourceIdentifier): HavePropertyMatchResult[String] = {
      val isCorrectType = sourceIdentifier.identifierType == identifierType
      val hasCorrectValue = sourceIdentifier.value == expectedValue
      val isCorrectOntology = sourceIdentifier.ontologyType == ontologyType

      HavePropertyMatchResult[String](
        matches = isCorrectType && hasCorrectValue && isCorrectOntology,
        propertyName = "id.sourceIdentifier",
        expectedValue = SourceIdentifier(
          value = expectedValue,
          ontologyType = ontologyType,
          identifierType = identifierType).toString,
        actualValue = sourceIdentifier.toString
      )
    }
  }

 // TODO: get expectedValue from identifiableObject.label
  def labelDerivedId(ontologyType: String, expectedValue: String)
  : HavePropertyMatcher[HasId[IdState.Unminted], String] = {
    new SourceIdentifierMatchers.HasLabelDerivedIdentifier(
      ontologyType = ontologyType,
      expectedValue = expectedValue)
  }

  def sourceIdentifier(ontologyType: String,
                       value: String,
                       identifierType: IdentifierType)
    : HavePropertyMatcher[HasId[IdState.Unminted], String] =
    new HasIdentifier(
      ontologyType: String,
      value: String,
      identifierType: IdentifierType)
}

object HasIdMatchers extends HasIdMatchers
