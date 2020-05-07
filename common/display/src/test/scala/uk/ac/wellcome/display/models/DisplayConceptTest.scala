package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal._

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
      concept = Period(label = "darkness"),
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

    assertDisplayConceptIsCorrect(
      concept = Concept(
        label = "darkness",
        id = Identified("dj4kndg5", sourceIdentifier)
      ),
      expectedDisplayConcept = DisplayConcept(
        id = Some("dj4kndg5"),
        identifiers = Some(List(DisplayIdentifier(sourceIdentifier))),
        label = "darkness"
      )
    )
  }

  it("reads an identified Period as a DisplayPeriod with identifiers") {
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Period"
    )

    assertDisplayConceptIsCorrect(
      concept = Period(
        label = "never",
        range = None,
        id = Identified("nrzbm3ah", sourceIdentifier)
      ),
      expectedDisplayConcept = DisplayPeriod(
        id = Some("nrzbm3ah"),
        identifiers = Some(List(DisplayIdentifier(sourceIdentifier))),
        label = "never"
      )
    )
  }

  it("reads an identified Place as a DisplayPlace with identifiers") {
    val sourceIdentifier = createSourceIdentifierWith(
      ontologyType = "Place"
    )

    assertDisplayConceptIsCorrect(
      concept = Place(
        label = "anywhere",
        id = Identified("axtswq4z", sourceIdentifier)
      ),
      expectedDisplayConcept = DisplayPlace(
        id = Some("axtswq4z"),
        identifiers = Some(List(DisplayIdentifier(sourceIdentifier))),
        label = "anywhere"
      )
    )
  }

  private def assertDisplayConceptIsCorrect(
    concept: AbstractConcept[Minted],
    expectedDisplayConcept: DisplayAbstractConcept
  ) = {
    val displayConcept =
      DisplayAbstractConcept(concept, includesIdentifiers = true)
    displayConcept shouldBe expectedDisplayConcept
  }
}
