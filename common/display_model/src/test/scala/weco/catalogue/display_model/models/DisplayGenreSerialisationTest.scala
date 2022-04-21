package weco.catalogue.display_model.models

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._
import Implicits._
import weco.catalogue.display_model.test.util.JsonMapperTestUtil

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
      id = IdState.Identified(
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
