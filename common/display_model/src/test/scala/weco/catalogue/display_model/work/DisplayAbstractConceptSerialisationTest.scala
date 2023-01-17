package weco.catalogue.display_model.work

import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.display_model.test.util.{
  DisplaySerialisationTestBase,
  JsonMapperTestUtil
}
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Concept, InstantRange, Period, Place}
import weco.http.json.DisplayJsonUtil._

import java.time.Instant

class DisplayAbstractConceptSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with IdentifiersGenerators {

  import weco.catalogue.display_model.Implicits._

  it("serialises an unidentified DisplayConcept") {
    assertObjectMapsToJson(
      DisplayConcept(
        id = None,
        identifiers = None,
        label = "conceptLabel"
      ),
      expectedJson = s"""
         |  {
         |    "label" : "conceptLabel",
         |    "type"  : "Concept"
         |  }
          """.stripMargin
    )
  }

  it("serialises an unidentified DisplayPeriod") {
    assertObjectMapsToJson(
      DisplayPeriod(
        id = None,
        identifiers = None,
        label = "periodLabel"
      ),
      expectedJson = s"""
         |  {
         |    "label" : "periodLabel",
         |    "type"  : "Period"
         |  }
          """.stripMargin
    )
  }

  it("serialises an unidentified DisplayPlace") {
    assertObjectMapsToJson(
      DisplayPlace(
        id = None,
        identifiers = None,
        label = "placeLabel"
      ),
      expectedJson = s"""
         |  {
         |    "label" : "placeLabel",
         |    "type"  : "Place"
         |  }
         """.stripMargin
    )
  }

  it("constructs a DisplayConcept from an identified Concept") {
    val concept = Concept(
      label = "conceptLabel",
      id = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Concept"
        )
      )
    )

    assertObjectMapsToJson(
      DisplayAbstractConcept(concept, includesIdentifiers = true),
      expectedJson = s"""
         |  {
         |    "id": "${concept.id.canonicalId}",
         |    "identifiers": [${identifier(concept.id.sourceIdentifier)}],
         |    "label" : "${concept.label}",
         |    "type"  : "Concept"
         |  }
          """.stripMargin
    )
  }

  it("serialises AbstractDisplayConcepts constructed from AbstractConcepts") {
    val concepts =
      List(
        Concept("conceptLabel"),
        Place("placeLabel"),
        Period("periodLabel", InstantRange(Instant.now, Instant.now))
      )
    assertObjectMapsToJson(
      concepts.map(DisplayAbstractConcept(_, includesIdentifiers = false)),
      expectedJson = s"""
          | [
          |    {
          |      "label" : "conceptLabel",
          |      "type"  : "Concept"
          |    },
          |    {
          |      "label" : "placeLabel",
          |      "type"  : "Place"
          |    },
          |    {
          |      "label" : "periodLabel",
          |      "type"  : "Period"
          |    }
          |  ]
          """.stripMargin
    )
  }
}
