package weco.pipeline.transformer.tei.generators

import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}

trait LabelDerivedIdentifiersGenerators {
  def labelDerivedPersonIdentifier(value: String): Identifiable =
    IdState.Identifiable(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = value,
        ontologyType = "Person"
      )
    )
}
