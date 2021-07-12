package weco.pipeline.calm_indexer.services

import io.circe.Json
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil._
import weco.json.utils.JsonAssertions

class CalmJsonOpsTest extends AnyFunSpec with Matchers with JsonAssertions {
  import CalmJsonOps._

  it("removes empty values") {
    val json = fromJson[Json](
      """
        |{
        |  "UserText2": [
        |    ""
        |  ],
        |  "UserWrapped1": [
        |    "Reproduction subject to usual conditions of Glasgow University Archive Services: educational use and condition of documents"
        |  ],
        |  "Originals": [
        |    "The original material is held at <a href=\"http://www.gla.ac.uk/services/archives/\"target=\"_blank\">Glasgow University Archive Services.</a> This catalogue is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics."
        |  ],
        |  "Location": ["GB", "GB"],
        |  "Language": ["EN", "FR"]
        |}
        |""".stripMargin).get

    val tidiedJson = json.tidy

    assertJsonStringsAreEqual(
      tidiedJson.noSpaces,
      s"""
         |{
         |  "UserWrapped1": "Reproduction subject to usual conditions of Glasgow University Archive Services: educational use and condition of documents",
         |  "Originals": "The original material is held at <a href=\\"http://www.gla.ac.uk/services/archives/\\"target=\\"_blank\\">Glasgow University Archive Services.</a> This catalogue is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics.",
         |  "Location": ["GB", "GB"],
         |  "Language": ["EN", "FR"]
         |}
         |""".stripMargin
    )
  }
}
