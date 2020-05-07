package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.display.test.util.JsonMapperTestUtil
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal._

class DisplayGenreSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with IdentifiersGenerators {

  it("serialises a DisplayGenre constructed from a Genre") {
    val concept0 = Concept("conceptLabel")
    val concept1 = Place("placeLabel")
    val concept2 = Period(
      label = "periodLabel",
      range = None,
      id = Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Period"
        )
      )
    )

    val genre = Genre(
      label = "genreLabel",
      concepts = List(concept0, concept1, concept2)
    )

    assertObjectMapsToJson(
      DisplayGenre(genre, includesIdentifiers = true),
      expectedJson = s"""
        {
          "label" : "genreLabel",
          "concepts" : [
            {
              "label" : "conceptLabel",
              "type" : "Concept"
            },
            {
              "label" : "placeLabel",
              "type" : "Place"
            },
            {
              "id": "${concept2.id.canonicalId}",
              "identifiers": [${identifier(concept2.id.sourceIdentifier)}],
              "label" : "periodLabel",
              "type" : "Period"
            }
          ],
          "type" : "Genre"
        }
      """
    )
  }
}
