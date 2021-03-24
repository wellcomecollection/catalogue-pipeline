package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.display.test.util.JsonMapperTestUtil
import uk.ac.wellcome.models.work.generators.SubjectGenerators
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._

class DisplaySubjectSerialisationTest
    extends AnyFunSpec
    with DisplaySerialisationTestBase
    with JsonMapperTestUtil
    with IdentifiersGenerators
    with SubjectGenerators {

  it("serialises a DisplaySubject constructed from a Subject") {
    val canonicalId2 = createCanonicalId

    val concept0 = Concept(label = "conceptLabel")
    val concept1 = Period(label = "periodLabel")
    val concept2 = Place(
      label = "placeLabel",
      id = IdState.Identified(
        canonicalId = canonicalId2,
        sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Place"
        )
      )
    )

    val subject = createSubjectWith(
      concepts = List(concept0, concept1, concept2)
    )

    assertObjectMapsToJson(
      DisplaySubject(subject, includesIdentifiers = true),
      expectedJson = s"""
        {
          "label" : "${subject.label}",
          "concepts" : [
            {
              "label" : "conceptLabel",
              "type" : "Concept"
            },
            {
              "label" : "periodLabel",
              "type" : "Period"
            },
            {
              "id": "$canonicalId2",
              "identifiers": [${identifier(concept2.id.sourceIdentifier)}],
              "label" : "placeLabel",
              "type" : "Place"
            }
          ],
          "type" : "Subject"
        }
      """
    )
  }

  it("serialises a DisplaySubject from a Subject with a Person concept") {
    val person = Person("Dolly Parton")
    val subject = createSubjectWith(concepts = List(person))
    assertObjectMapsToJson(
      DisplaySubject(subject, includesIdentifiers = true),
      expectedJson = s"""
        {
          "label" : "${subject.label}",
          "concepts" : [
            {
              "label" : "Dolly Parton",
              "type" : "Person"
            }],
          "type" : "Subject"
        }
      """
    )
  }

  it("serialises a DisplaySubject from a Subject with a Agent concept") {
    val agent = Agent("Dolly Parton")
    val subject = createSubjectWith(concepts = List(agent))
    assertObjectMapsToJson(
      DisplaySubject(subject, includesIdentifiers = true),
      expectedJson = s"""
        {
          "label" : "${subject.label}",
          "concepts" : [
            {
              "label" : "Dolly Parton",
              "type" : "Agent"
            }],
          "type" : "Subject"
        }
      """
    )
  }

  it("serialises a DisplaySubject from a Subject with a Organisation concept") {
    val organisation = Organisation("Dolly Parton")
    val subject = createSubjectWith(concepts = List(organisation))
    assertObjectMapsToJson(
      DisplaySubject(subject, includesIdentifiers = true),
      expectedJson = s"""
        {
          "label" : "${subject.label}",
          "concepts" : [
            {
              "label" : "Dolly Parton",
              "type" : "Organisation"
            }],
          "type" : "Subject"
        }
      """
    )
  }
}
