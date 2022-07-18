package weco.pipeline.transformer.sierra.transformers.matchers

import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher}
import weco.catalogue.internal_model.identifiers.{
  HasId,
  IdState,
  IdentifierType,
  SourceIdentifier
}

trait SourceIdentifierMatchers {
  class HasIdentifier(ontologyType: String, expectedValue: String, identifierType: IdentifierType)
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

  class HasLabelDerivedIdentifier(ontologyType: String, expectedValue: String)
      extends HasIdentifier(ontologyType: String, expectedValue: String, identifierType = IdentifierType.LabelDerived)

  class HasMeshIdentifier(ontologyType: String, expectedValue: String)
      extends HasIdentifier(ontologyType: String, expectedValue: String, identifierType =  IdentifierType.MESH)

  class HasLCSubjectsIdentifier(ontologyType: String, expectedValue: String)
    extends HasIdentifier(ontologyType: String, expectedValue: String, IdentifierType.LCSubjects)
}

object SourceIdentifierMatchers extends SourceIdentifierMatchers
