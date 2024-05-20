package weco.pipeline.sierra_indexer.services

import io.circe.parser._
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SierraJsonOpsTest extends AnyFunSpec with Matchers with EitherValues {
  import SierraJsonOps._

  describe("varFields") {
    it("gets the varFields from a Sierra API response") {
      val varFieldsStrings = List(
        """
          |{
          |  "fieldTag" : "b",
          |  "content" : "22501328220"
          |}
          |""".stripMargin,
        """
          |{
          |  "fieldTag" : "c",
          |  "marcTag" : "949",
          |  "ind1" : " ",
          |  "ind2" : " ",
          |  "subfields" : [
          |    {
          |      "tag" : "a",
          |      "content" : "/RHO"
          |    }
          |  ]
          |}
          |""".stripMargin,
        """
          |{
          |  "fieldTag" : "g",
          |  "content" : "P"
          |}
          |""".stripMargin
      )

      val jsonString =
        s"""
          |{
          |  "id" : "1464045",
          |  "updatedDate" : "2013-12-12T13:56:07Z",
          |  "deleted" : false,
          |  "bibIds" : ["1536695"],
          |  "varFields" : [${varFieldsStrings.mkString(", ")}]
          |}
          |""".stripMargin

      println(jsonString)

      val json = parse(jsonString).value

      json.varFields shouldBe varFieldsStrings.map { parse(_).value }
    }

    it("returns an empty list if there are no varFields") {
      val jsonString =
        s"""
           |{
           |  "id" : "1464045",
           |  "updatedDate" : "2013-12-12T13:56:07Z",
           |  "deleted" : true
           |}
           |""".stripMargin

      val json = parse(jsonString).value

      json.varFields shouldBe empty
    }
  }

  describe("varFields") {
    it("gets the fixedFields from a Sierra API response") {
      val fixedFields = Map(
        "86" ->
          """
            |{
            |  "label" : "AGENCY",
            |  "value" : "1"
            |}
            |""".stripMargin,
        "265" ->
          """
            |{
            |  "label" : "Inherit Location",
            |  "value" : false
            |}
            |""".stripMargin
      )

      val fixedFieldsJson =
        fixedFields
          .map {
            case (code, json) =>
              s"""
              |"$code": $json
              |""".stripMargin
          }

      val jsonString =
        s"""
           |{
           |  "id" : "1464045",
           |  "updatedDate" : "2013-12-12T13:56:07Z",
           |  "deleted" : false,
           |  "bibIds" : ["1536695"],
           |  "fixedFields" : {${fixedFieldsJson.mkString(", ")}}
           |}
           |""".stripMargin

      val json = parse(jsonString).value

      println(jsonString)

      json.fixedFields shouldBe fixedFields.map {
        case (code, json) => code -> parse(json).value
      }
    }

    it("returns an empty list if there are no fixedFields") {
      val jsonString =
        s"""
           |{
           |  "id" : "1464045",
           |  "updatedDate" : "2013-12-12T13:56:07Z",
           |  "deleted" : true
           |}
           |""".stripMargin

      val json = parse(jsonString).value

      json.fixedFields shouldBe empty
    }
  }

  describe("remainder") {
    it("removes the varFields and fixedFields from a Sierra API response") {
      val jsonString =
        s"""
           |{
           |  "id": "1464045",
           |  "updatedDate": "2013-12-12T13:56:07Z",
           |  "deleted": false,
           |  "bibIds": [
           |    "1536695"
           |  ],
           |  "varFields": [
           |    {
           |      "fieldTag": "b",
           |      "content": "22501328220"
           |    },
           |    {
           |      "fieldTag": "g",
           |      "content": "P"
           |    }
           |  ],
           |  "fixedFields": {
           |    "81" : {
           |      "label" : "RECORD #",
           |      "value" : "1851557"
           |    },
           |    "110" : {
           |      "label" : "LYCIRC",
           |      "value" : 0
           |    }
           |  }
           |}
           |
           |""".stripMargin

      val json = parse(jsonString).value

      json.remainder shouldBe parse(
        """
          |{
          |  "id": "1464045",
          |  "updatedDate": "2013-12-12T13:56:07Z",
          |  "deleted": false,
          |  "bibIds": [
          |    "1536695"
          |  ]
          |}
          |""".stripMargin
      ).value
    }

    it(
      "returns the remainder unmodified if there are no varFields or fixedFields"
    ) {
      val jsonString =
        s"""
           |{
           |  "id" : "1464045",
           |  "updatedDate" : "2013-12-12T13:56:07Z",
           |  "deleted" : true
           |}
           |""".stripMargin

      val json = parse(jsonString).value

      json.remainder shouldBe json
    }
  }
}
