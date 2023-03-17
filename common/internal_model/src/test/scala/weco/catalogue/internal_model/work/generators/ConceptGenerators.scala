package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.{Concept, GenreConcept}
import weco.fixtures.RandomGenerators

trait IdentifiedConceptGenerators
    extends RandomGenerators
    with IdentifiersGenerators {

  protected def createConcept(
    canonicalId: String = createUnderlyingCanonicalId,
    label: String = randomAlphanumeric(15)
  ): Concept[IdState.Identified] =
    new Concept(
      id = IdState.Identified(
        canonicalId = CanonicalId(canonicalId),
        sourceIdentifier = createSourceIdentifierWith(ontologyType = "Concept"),
        otherIdentifiers = Nil
      ),
      label = label
    )

  protected def createGenreConcept(
    canonicalId: String = createUnderlyingCanonicalId,
    label: String = randomAlphanumeric(15)
  ): GenreConcept[IdState.Identified] =
    GenreConcept[IdState.Identified](
      id = IdState.Identified(
        canonicalId = CanonicalId(canonicalId),
        sourceIdentifier = createSourceIdentifierWith(ontologyType = "Genre"),
        otherIdentifiers = Nil
      ),
      label = label
    )
}
