package weco.pipeline.transformer.generators

import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}

trait LabelDerivedIdentifiersGenerators {
  def labelDerivedAgentIdentifier(value: String): Identifiable =
    IdState.Identifiable(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = value,
        ontologyType = "Agent"
      )
    )

  def labelDerivedPersonIdentifier(value: String): Identifiable =
    IdState.Identifiable(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = value,
        ontologyType = "Person"
      )
    )
}
