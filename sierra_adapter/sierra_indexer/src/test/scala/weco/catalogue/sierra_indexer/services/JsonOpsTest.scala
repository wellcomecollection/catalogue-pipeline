package weco.catalogue.sierra_indexer.services

import io.circe.parser._
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class JsonOpsTest extends AnyFunSpec with Matchers with EitherValues {
  import JsonOps._

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
}
