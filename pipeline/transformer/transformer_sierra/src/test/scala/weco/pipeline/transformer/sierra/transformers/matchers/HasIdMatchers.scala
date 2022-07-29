package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.identifiers.{
  HasId,
  IdState,
  IdentifierType,
  SourceIdentifier
}

trait HasIdMatchers {

  class HasIdentifier[T](ontologyType: String,
                         value: String,
                         identifierType: IdentifierType)
      extends HavePropertyMatcher[HasId[T], String] {
    def apply(identifiableObject: HasId[T]): HavePropertyMatchResult[String] = {
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
      val hasCorrectValue = sourceIdentifier.value == value
      val isCorrectOntology = sourceIdentifier.ontologyType == ontologyType

      HavePropertyMatchResult[String](
        matches = isCorrectType && hasCorrectValue && isCorrectOntology,
        propertyName = "id.sourceIdentifier",
        expectedValue = SourceIdentifier(
          value = value,
          ontologyType = ontologyType,
          identifierType = identifierType).toString,
        actualValue = sourceIdentifier.toString
      )
    }
  }

  def sourceIdentifier[T](
    ontologyType: String,
    value: String,
    identifierType: IdentifierType): HavePropertyMatcher[HasId[T], String] =
    new HasIdentifier(
      ontologyType: String,
      value: String,
      identifierType: IdentifierType)
}

object HasIdMatchers extends HasIdMatchers
