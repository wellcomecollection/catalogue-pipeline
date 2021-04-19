package uk.ac.wellcome.platform.transformer.sierra.source

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import SierraBibData._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLanguage

class SierraBibDataTest extends AnyFunSpec with Matchers {
  it("decodes a bibData with language") {
    val bibDataJson =
      s"""
         |{
         |  "lang": {
         |    "code": "eng",
         |    "name": "English"
         |  }
         |}""".stripMargin

    fromJson[SierraBibData](bibDataJson).get shouldBe SierraBibData(
      lang = Some(SierraSourceLanguage("eng", "English")))
  }

  it("decodes a language with empty code as None") {
    val bibDataJson =
      s"""
         |{
         |  "lang": {
         |    "code": " "
         |  }
         |}""".stripMargin

    fromJson[SierraBibData](bibDataJson).get shouldBe SierraBibData()
  }
  it("fails to decode a bibData without name and non-empty code") {
    val bibDataJson =
      s"""
         |{
         |  "lang": {
         |    "code": "blah"
         |  }
         |}""".stripMargin

    intercept[JsonDecodingError] { fromJson[SierraBibData](bibDataJson).get }
  }
}
