package uk.ac.wellcome.platform.idminter.steps

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import io.circe.parser._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.platform.idminter.fixtures.SqlIdentifiersGenerators
import uk.ac.wellcome.platform.idminter.models.Identifier

import scala.util.{Failure, Success}

class SourceIdentifierEmbedderTest
    extends FunSpec
    with IdentifiersGenerators
    with Matchers
    with JsonAssertions
    with SqlIdentifiersGenerators {
  describe("scan") {
    it("retrieves a sourceIdentifier at the root of the json") {
      val sourceIdentifier = createSourceIdentifier
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifier.identifierType.id}",
        |      "label": "${sourceIdentifier.identifierType.label}",
        |      "ontologyType": "IdentifierType"
        |    },
        |    "value": "${sourceIdentifier.value}",
        |    "ontologyType": "${sourceIdentifier.ontologyType}"
        |  }
        |}
      """.stripMargin
      val json = parse(jsonString).right.get

      SourceIdentifierEmbedder
        .scan(json)
        .get shouldBe List(sourceIdentifier)
    }

    it("retrieves multiple sourceIdentifiers nested in the json") {
      val sourceIdentifiers = (1 to 4).map(_ => createSourceIdentifier)
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifiers(0).identifierType.id}",
        |      "label": "${sourceIdentifiers(0).identifierType.label}",
        |      "ontologyType": "IdentifierType"
        |    },
        |    "value": "${sourceIdentifiers(0).value}",
        |    "ontologyType": "${sourceIdentifiers(0).ontologyType}"
        |  },
        |  "moreThings": [
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(1).identifierType.id}",
        |           "label": "${sourceIdentifiers(1).identifierType.label}",
        |           "ontologyType": "IdentifierType"
        |         },
        |         "value": "${sourceIdentifiers(1).value}",
        |         "ontologyType": "${sourceIdentifiers(1).ontologyType}"
        |       }
        |     },
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(2).identifierType.id}",
        |           "label": "${sourceIdentifiers(2).identifierType.label}",
        |           "ontologyType": "IdentifierType"
        |         },
        |         "value": "${sourceIdentifiers(2).value}",
        |         "ontologyType": "${sourceIdentifiers(2).ontologyType}"
        |       },
        |       "evenMoreThings": [
        |         {
        |           "sourceIdentifier": {
        |             "identifierType": {
        |               "id": "${sourceIdentifiers(3).identifierType.id}",
        |               "label": "${sourceIdentifiers(3).identifierType.label}",
        |               "ontologyType": "IdentifierType"
        |             },
        |             "value": "${sourceIdentifiers(3).value}",
        |             "ontologyType": "${sourceIdentifiers(3).ontologyType}"
        |           }
        |         }       
        |       ]
        |     }
        |  ]
        |}
      """.stripMargin
      val json = parse(jsonString).right.get
      SourceIdentifierEmbedder
        .scan(json)
        .get should contain theSameElementsAs (sourceIdentifiers)
    }

    it("throws an exception if it cannot parse a sourceIdentifier") {
      val json =
        """
          |{
          | "sourceIdentifier": {
          |   "something": "something"
          | }
          |}
          |""".stripMargin

      SourceIdentifierEmbedder.scan(parse(json).right.get) shouldBe a[
        Failure[_]]

    }
  }
  describe("update") {
    it("modifies json to add a single canonicalId in the root") {
      val sourceIdentifier = createSourceIdentifier
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifier.identifierType.id}",
        |      "label": "${sourceIdentifier.identifierType.label}",
        |      "ontologyType": "IdentifierType"
        |    },
        |    "value": "${sourceIdentifier.value}",
        |    "ontologyType": "${sourceIdentifier.ontologyType}"
        |  }
        |}
      """.stripMargin
      val json = parse(jsonString).right.get
      val identifier = Identifier(
        canonicalId = createCanonicalId,
        sourceIdentifier = sourceIdentifier)
      val identified = SourceIdentifierEmbedder.update(
        json,
        Map(sourceIdentifier -> identifier))

      identified shouldBe a[Success[_]]
      val updatedJsonString = identified.get.spaces2
      assertJsonStringsAreEqual(
        updatedJsonString,
        s"""
           |{
           |  "sourceIdentifier": {
           |    "identifierType": {
           |      "id": "${sourceIdentifier.identifierType.id}",
           |      "label": "${sourceIdentifier.identifierType.label}",
           |      "ontologyType": "IdentifierType"
           |    },
           |    "value": "${sourceIdentifier.value}",
           |    "ontologyType": "${sourceIdentifier.ontologyType}"
           |  },
           |  "canonicalId": "${identifier.CanonicalId}"
           |}
        """.stripMargin
      )
    }

    it("modifies json to add multiple nested canonicalIds") {
      val sourceIdentifiers = (1 to 4).map(_ => createSourceIdentifier)
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifiers(0).identifierType.id}",
        |      "label": "${sourceIdentifiers(0).identifierType.label}",
        |      "ontologyType": "IdentifierType"
        |    },
        |    "value": "${sourceIdentifiers(0).value}",
        |    "ontologyType": "${sourceIdentifiers(0).ontologyType}"
        |  },
        |  "moreThings": [
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(1).identifierType.id}",
        |           "label": "${sourceIdentifiers(1).identifierType.label}",
        |           "ontologyType": "IdentifierType"
        |         },
        |         "value": "${sourceIdentifiers(1).value}",
        |         "ontologyType": "${sourceIdentifiers(1).ontologyType}"
        |       }
        |     },
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(2).identifierType.id}",
        |           "label": "${sourceIdentifiers(2).identifierType.label}",
        |           "ontologyType": "IdentifierType"
        |         },
        |         "value": "${sourceIdentifiers(2).value}",
        |         "ontologyType": "${sourceIdentifiers(2).ontologyType}"
        |       },
        |       "evenMoreThings": [
        |         {
        |           "sourceIdentifier": {
        |             "identifierType": {
        |               "id": "${sourceIdentifiers(3).identifierType.id}",
        |               "label": "${sourceIdentifiers(3).identifierType.label}",
        |               "ontologyType": "IdentifierType"
        |             },
        |             "value": "${sourceIdentifiers(3).value}",
        |             "ontologyType": "${sourceIdentifiers(3).ontologyType}"
        |           }
        |         }       
        |       ]
        |     }
        |  ]
        |}
      """.stripMargin
      val json = parse(jsonString).right.get
      val identifiers = sourceIdentifiers.map { sourceIdentifier =>
        sourceIdentifier -> Identifier(createCanonicalId, sourceIdentifier)
      }.toMap
      val canonicalIds =
        sourceIdentifiers.flatMap(identifiers.get).map(_.CanonicalId)
      val identified = SourceIdentifierEmbedder.update(
        json,
        identifiers
      )

      identified shouldBe a[Success[_]]
      val updatedJsonString = identified.get.spaces2
      assertJsonStringsAreEqual(
        updatedJsonString,
        s"""
          |{
          |  "sourceIdentifier": {
          |    "identifierType": {
          |      "id": "${sourceIdentifiers(0).identifierType.id}",
          |      "label": "${sourceIdentifiers(0).identifierType.label}",
          |      "ontologyType": "IdentifierType"
          |    },
          |    "value": "${sourceIdentifiers(0).value}",
          |    "ontologyType": "${sourceIdentifiers(0).ontologyType}"
          |  },
          |  "canonicalId": "${canonicalIds(0)}",
          |  "moreThings": [
          |     {
          |       "sourceIdentifier": {
          |         "identifierType": {
          |           "id": "${sourceIdentifiers(1).identifierType.id}",
          |           "label": "${sourceIdentifiers(1).identifierType.label}",
          |           "ontologyType": "IdentifierType"
          |         },
          |         "value": "${sourceIdentifiers(1).value}",
          |         "ontologyType": "${sourceIdentifiers(1).ontologyType}"
          |       },
          |       "canonicalId": "${canonicalIds(1)}"
          |     },
          |     {
          |       "sourceIdentifier": {
          |         "identifierType": {
          |           "id": "${sourceIdentifiers(2).identifierType.id}",
          |           "label": "${sourceIdentifiers(2).identifierType.label}",
          |           "ontologyType": "IdentifierType"
          |         },
          |         "value": "${sourceIdentifiers(2).value}",
          |         "ontologyType": "${sourceIdentifiers(2).ontologyType}"
          |       },
          |       "canonicalId": "${canonicalIds(2)}",
          |       "evenMoreThings": [
          |         {
          |           "sourceIdentifier": {
          |             "identifierType": {
          |               "id": "${sourceIdentifiers(3).identifierType.id}",
          |               "label": "${sourceIdentifiers(3).identifierType.label}",
          |               "ontologyType": "IdentifierType"
          |             },
          |             "value": "${sourceIdentifiers(3).value}",
          |             "ontologyType": "${sourceIdentifiers(3).ontologyType}"
          |           },
          |           "canonicalId": "${canonicalIds(3)}"
          |         }       
          |       ]
          |     }
          |  ]
          |}
        """.stripMargin
      )
    }

    it("replaces identifiedType with type") {
      val sourceIdentifier1 = createSourceIdentifier
      val sourceIdentifier2 = createSourceIdentifier
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifier1.identifierType.id}",
        |      "label": "${sourceIdentifier1.identifierType.label}",
        |      "ontologyType": "IdentifierType"
        |    },
        |    "value": "${sourceIdentifier1.value}",
        |    "ontologyType": "${sourceIdentifier1.ontologyType}"
        |  },
        |  "identifiedType": "NewType",
        |  "moreThings": [
        |    {
        |      "sourceIdentifier": {
        |        "identifierType": {
        |          "id": "${sourceIdentifier2.identifierType.id}",
        |          "label": "${sourceIdentifier2.identifierType.label}",
        |          "ontologyType": "IdentifierType"
        |        },
        |        "value": "${sourceIdentifier2.value}",
        |        "ontologyType": "${sourceIdentifier2.ontologyType}"
        |      },
        |      "identifiedType": "AnotherNewType"
        |    }
        |  ]
        |}
      """.stripMargin
      val json = parse(jsonString).right.get
      val identifiers = Map(
        sourceIdentifier1 -> Identifier(createCanonicalId, sourceIdentifier1),
        sourceIdentifier2 -> Identifier(createCanonicalId, sourceIdentifier2)
      )
      val identified = SourceIdentifierEmbedder.update(
        json,
        identifiers
      )

      identified shouldBe a[Success[_]]
      val updatedJsonString = identified.get.spaces2
      assertJsonStringsAreEqual(
        updatedJsonString,
        s"""
           |{
           |  "sourceIdentifier": {
           |    "identifierType": {
           |      "id": "${sourceIdentifier1.identifierType.id}",
           |      "label": "${sourceIdentifier1.identifierType.label}",
           |      "ontologyType": "IdentifierType"
           |    },
           |    "value": "${sourceIdentifier1.value}",
           |    "ontologyType": "${sourceIdentifier1.ontologyType}"
           |  },
           |  "canonicalId": "${identifiers(sourceIdentifier1).CanonicalId}",
           |  "type": "NewType",
           |  "moreThings": [
           |    {
           |      "sourceIdentifier": {
           |        "identifierType": {
           |          "id": "${sourceIdentifier2.identifierType.id}",
           |          "label": "${sourceIdentifier2.identifierType.label}",
           |          "ontologyType": "IdentifierType"
           |        },
           |        "value": "${sourceIdentifier2.value}",
           |        "ontologyType": "${sourceIdentifier2.ontologyType}"
           |      },
           |      "canonicalId": "${identifiers(sourceIdentifier2).CanonicalId}",
           |      "type": "AnotherNewType"
           |    }
           |  ]
           |}
      """.stripMargin
      )
    }

    it(
      "fails if it cannot match the identifier to any sourceIdentifier in the json") {
      val sourceIdentifier = createSourceIdentifier
      val otherSourceIdentifier = createSourceIdentifier
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifier.identifierType.id}",
        |      "label": "${sourceIdentifier.identifierType.label}",
        |      "ontologyType": "IdentifierType"
        |    },
        |    "value": "${sourceIdentifier.value}",
        |    "ontologyType": "${sourceIdentifier.ontologyType}"
        |  }
        |}
      """.stripMargin
      val json = parse(jsonString).right.get
      val identifier =
        createSQLIdentifierWith(sourceIdentifier = otherSourceIdentifier)
      val identified = SourceIdentifierEmbedder.update(
        json,
        Map(otherSourceIdentifier -> identifier))

      identified shouldBe a[Failure[_]]
    }
  }
}
