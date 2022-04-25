package weco.catalogue.display_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.display_model.identifiers.DisplayIdentifier
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._

import java.time.Instant

class DisplayConceptTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators {

  it("reads an unidentified generic Concept as a DisplayConcept") {
    assertDisplayConceptIsCorrect(
      concept = Concept(label = "evil"),
      expectedDisplayConcept = DisplayConcept(
        id = None,
        identifiers = None,
        label = "evil"
      )
    )
  }

  it("reads an unidentified Period as a DisplayPeriod") {
    assertDisplayConceptIsCorrect(
      concept =
        Period(label = "darkness", InstantRange(Instant.now, Instant.now)),
      expectedDisplayConcept = DisplayPeriod(
        id = None,
        identifiers = None,
        label = "darkness"
      )
    )
  }

  it("reads an unidentified Place as a DisplayPlace") {
    assertDisplayConceptIsCorrect(
      concept = Place(label = "nowhere"),
      expectedDisplayConcept = DisplayPlace(
        id = None,
        identifiers = None,
        label = "nowhere"
      )
    )
  }

  it("reads an identified Concept as a DisplayConcept with identifiers") {
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Concept"
    )

    val canonicalId = createCanonicalId

    assertDisplayConceptIsCorrect(
      concept = Concept(
        label = "darkness",
        id = IdState.Identified(canonicalId, sourceIdentifier)
      ),
      expectedDisplayConcept = DisplayConcept(
        id = Some(canonicalId.underlying),
        identifiers = Some(List(DisplayIdentifier(sourceIdentifier))),
        label = "darkness"
      )
    )
  }

  it("reads an identified Period as a DisplayPeriod with identifiers") {
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Period"
    )

    val canonicalId = createCanonicalId

    assertDisplayConceptIsCorrect(
      concept = Period(
        label = "never",
        range = None,
        id = IdState.Identified(canonicalId, sourceIdentifier)
      ),
      expectedDisplayConcept = DisplayPeriod(
        id = Some(canonicalId.underlying),
        identifiers = Some(List(DisplayIdentifier(sourceIdentifier))),
        label = "never"
      )
    )
  }

  it("reads an identified Place as a DisplayPlace with identifiers") {
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Place"
    )

    val canonicalId = createCanonicalId

    assertDisplayConceptIsCorrect(
      concept = Place(
        label = "anywhere",
        id = IdState.Identified(canonicalId, sourceIdentifier)
      ),
      expectedDisplayConcept = DisplayPlace(
        id = Some(canonicalId.underlying),
        identifiers = Some(List(DisplayIdentifier(sourceIdentifier))),
        label = "anywhere"
      )
    )
  }

  private def assertDisplayConceptIsCorrect(
    concept: AbstractConcept[IdState.Minted],
    expectedDisplayConcept: DisplayAbstractConcept
  ) = {
    val displayConcept =
      DisplayAbstractConcept(concept, includesIdentifiers = true)
    displayConcept shouldBe expectedDisplayConcept
  }
}
