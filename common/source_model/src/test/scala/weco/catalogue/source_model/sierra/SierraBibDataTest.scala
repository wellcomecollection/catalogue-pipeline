package weco.catalogue.source_model.sierra

import org.scalatest.TryValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.source_model.sierra.source.SierraSourceLanguage

import SierraBibData._

class SierraBibDataTest extends AnyFunSpec with Matchers with TryValues {
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

  it("decodes a bib with no language name") {
    // This is a minimal example based on b16675617, as retrieved 19 April 2021
    // It was failing in the Sierra transformer.
    val bibDataJson =
      s"""{
         |  "deleted": false,
         |  "suppressed": false,
         |  "lang": {
         |    "code": "tgl"
         |  },
         |  "locations": [],
         |  "fixedFields": {
         |    "24": {
         |      "label": "LANG",
         |      "value": "tgl"
         |    }
         |  },
         |  "varFields": []
         |}""".stripMargin

    val bibData = fromJson[SierraBibData](bibDataJson).success.value
    bibData.lang shouldBe Some(SierraSourceLanguage(code = "tgl", name = None))
  }
}
