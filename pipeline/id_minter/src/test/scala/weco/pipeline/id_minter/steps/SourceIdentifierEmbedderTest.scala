package weco.pipeline.id_minter.steps

import org.scalatest.matchers.should.Matchers
import io.circe.parser._
import org.scalatest.funspec.AnyFunSpec
import weco.json.utils.JsonAssertions
import weco.pipeline.id_minter.fixtures.SqlIdentifiersGenerators

import scala.util.{Failure, Success}

class SourceIdentifierEmbedderTest
    extends AnyFunSpec
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
        |      "id": "${sourceIdentifier.identifierType.id}"
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
        |      "id": "${sourceIdentifiers(0).identifierType.id}"
        |    },
        |    "value": "${sourceIdentifiers(0).value}",
        |    "ontologyType": "${sourceIdentifiers(0).ontologyType}"
        |  },
        |  "moreThings": [
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(1).identifierType.id}"
        |         },
        |         "value": "${sourceIdentifiers(1).value}",
        |         "ontologyType": "${sourceIdentifiers(1).ontologyType}"
        |       }
        |     },
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(2).identifierType.id}"
        |         },
        |         "value": "${sourceIdentifiers(2).value}",
        |         "ontologyType": "${sourceIdentifiers(2).ontologyType}"
        |       },
        |       "evenMoreThings": [
        |         {
        |           "sourceIdentifier": {
        |             "identifierType": {
        |               "id": "${sourceIdentifiers(3).identifierType.id}"
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
        .get should contain theSameElementsAs sourceIdentifiers
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

      SourceIdentifierEmbedder
        .scan(parse(json).right.get) shouldBe a[Failure[_]]

    }
  }
  describe("update") {
    it("modifies json to add a single canonicalId in the root") {
      val sourceIdentifier = createSourceIdentifier
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifier.identifierType.id}"
        |    },
        |    "value": "${sourceIdentifier.value}",
        |    "ontologyType": "${sourceIdentifier.ontologyType}"
        |  }
        |}
      """.stripMargin
      val json = parse(jsonString).right.get

      val canonicalId = createCanonicalId

      val identified = SourceIdentifierEmbedder.update(
        json,
        Map(sourceIdentifier -> canonicalId)
      )

      identified shouldBe a[Success[_]]
      val updatedJsonString = identified.get.spaces2
      assertJsonStringsAreEqual(
        updatedJsonString,
        s"""
           |{
           |  "sourceIdentifier": {
           |    "identifierType": {
           |      "id": "${sourceIdentifier.identifierType.id}"
           |    },
           |    "value": "${sourceIdentifier.value}",
           |    "ontologyType": "${sourceIdentifier.ontologyType}"
           |  },
           |  "canonicalId": "$canonicalId"
           |}
        """.stripMargin
      )
    }

    it("modifies json to add multiple nested canonicalIds") {
      val sourceIdentifiers = (1 to 4).map(_ => createSourceIdentifier)
      val canonicalIds = (1 to 4).map(_ => createCanonicalId)
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifiers(0).identifierType.id}"
        |    },
        |    "value": "${sourceIdentifiers(0).value}",
        |    "ontologyType": "${sourceIdentifiers(0).ontologyType}"
        |  },
        |  "moreThings": [
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(1).identifierType.id}"
        |         },
        |         "value": "${sourceIdentifiers(1).value}",
        |         "ontologyType": "${sourceIdentifiers(1).ontologyType}"
        |       }
        |     },
        |     {
        |       "sourceIdentifier": {
        |         "identifierType": {
        |           "id": "${sourceIdentifiers(2).identifierType.id}"
        |         },
        |         "value": "${sourceIdentifiers(2).value}",
        |         "ontologyType": "${sourceIdentifiers(2).ontologyType}"
        |       },
        |       "evenMoreThings": [
        |         {
        |           "sourceIdentifier": {
        |             "identifierType": {
        |               "id": "${sourceIdentifiers(3).identifierType.id}"
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
      val identifiers = sourceIdentifiers.zip(canonicalIds).toMap
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
          |      "id": "${sourceIdentifiers(0).identifierType.id}"
          |    },
          |    "value": "${sourceIdentifiers(0).value}",
          |    "ontologyType": "${sourceIdentifiers(0).ontologyType}"
          |  },
          |  "canonicalId": "${canonicalIds(0)}",
          |  "moreThings": [
          |     {
          |       "sourceIdentifier": {
          |         "identifierType": {
          |           "id": "${sourceIdentifiers(1).identifierType.id}"
          |         },
          |         "value": "${sourceIdentifiers(1).value}",
          |         "ontologyType": "${sourceIdentifiers(1).ontologyType}"
          |       },
          |       "canonicalId": "${canonicalIds(1)}"
          |     },
          |     {
          |       "sourceIdentifier": {
          |         "identifierType": {
          |           "id": "${sourceIdentifiers(2).identifierType.id}"
          |         },
          |         "value": "${sourceIdentifiers(2).value}",
          |         "ontologyType": "${sourceIdentifiers(2).ontologyType}"
          |       },
          |       "canonicalId": "${canonicalIds(2)}",
          |       "evenMoreThings": [
          |         {
          |           "sourceIdentifier": {
          |             "identifierType": {
          |               "id": "${sourceIdentifiers(3).identifierType.id}"
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
        |      "id": "${sourceIdentifier1.identifierType.id}"
        |    },
        |    "value": "${sourceIdentifier1.value}",
        |    "ontologyType": "${sourceIdentifier1.ontologyType}"
        |  },
        |  "identifiedType": "NewType",
        |  "moreThings": [
        |    {
        |      "sourceIdentifier": {
        |        "identifierType": {
        |          "id": "${sourceIdentifier2.identifierType.id}"
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
        sourceIdentifier1 -> createCanonicalId,
        sourceIdentifier2 -> createCanonicalId
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
           |      "id": "${sourceIdentifier1.identifierType.id}"
           |    },
           |    "value": "${sourceIdentifier1.value}",
           |    "ontologyType": "${sourceIdentifier1.ontologyType}"
           |  },
           |  "canonicalId": "${identifiers(sourceIdentifier1)}",
           |  "type": "NewType",
           |  "moreThings": [
           |    {
           |      "sourceIdentifier": {
           |        "identifierType": {
           |          "id": "${sourceIdentifier2.identifierType.id}"
           |        },
           |        "value": "${sourceIdentifier2.value}",
           |        "ontologyType": "${sourceIdentifier2.ontologyType}"
           |      },
           |      "canonicalId": "${identifiers(sourceIdentifier2)}",
           |      "type": "AnotherNewType"
           |    }
           |  ]
           |}
      """.stripMargin
      )
    }

    it(
      "fails if it cannot match the identifier to any sourceIdentifier in the json"
    ) {
      val sourceIdentifier = createSourceIdentifier
      val jsonString = s"""
        |{
        |  "sourceIdentifier": {
        |    "identifierType": {
        |      "id": "${sourceIdentifier.identifierType.id}"
        |    },
        |    "value": "${sourceIdentifier.value}",
        |    "ontologyType": "${sourceIdentifier.ontologyType}"
        |  }
        |}
      """.stripMargin
      val json = parse(jsonString).right.get
      val identified = SourceIdentifierEmbedder.update(
        json,
        identifiers = Map.empty
      )

      identified shouldBe a[Failure[_]]
    }
  }
}
