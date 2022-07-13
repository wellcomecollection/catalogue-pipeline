package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.identifiers.{HasId, IdState, IdentifierType, SourceIdentifier}

trait SourceIdentifierMatchers {

  class HasLabelDerivedIdentifier(ontologyType: String, expectedValue: String) extends HavePropertyMatcher[HasId[IdState], String] {
    def apply(identifiableObject: HasId[IdState]):  HavePropertyMatchResult[String] = {
      identifiableObject.id match {
        case identified: IdState.Identifiable => matchIdentifiableId(identified.sourceIdentifier)
        case _ => HavePropertyMatchResult[String](
          matches = false, propertyName = "id", expectedValue = IdState.Identifiable.toString, actualValue = identifiableObject.id.toString
        )
      }
    }

     protected def matchIdentifiableId(sourceIdentifier: SourceIdentifier): HavePropertyMatchResult[String] = {
      val isLabelDerived = sourceIdentifier.identifierType == IdentifierType.LabelDerived
      val hasCorrectValue = sourceIdentifier.value == expectedValue
      val isCorrectOntology = sourceIdentifier.ontologyType == ontologyType

      HavePropertyMatchResult[String](
        matches = isLabelDerived && hasCorrectValue && isCorrectOntology,
        propertyName = "id.sourceIdentifier",
        expectedValue = SourceIdentifier(value = expectedValue, ontologyType = ontologyType, identifierType = IdentifierType.LabelDerived).toString,
        actualValue = sourceIdentifier.toString
      )
    }
  }
}

object SourceIdentifierMatchers extends SourceIdentifierMatchers
