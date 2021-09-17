package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

import scala.xml.Elem

class TeiLanguagesTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with TeiGenerators {
  it("gets a single language from the TEI") {
    val xml: Elem =
      teiXml(
        languages = List(
          mainLanguage("sa", "Sanskrit")
        )
      )

    TeiLanguages(xml).value shouldBe List(
      Language(id = "san", label = "Sanskrit"))
  }

  it("gets multiple languages from TEI") {
    val xml: Elem =
      teiXml(
        languages = List(
          mainLanguage("sa", "Sanskrit"),
          otherLanguage("la", "Latin")
        )
      )

    TeiLanguages(xml).value shouldBe List(
      Language(id = "san", label = "Sanskrit"),
      Language(id = "lat", label = "Latin")
    )
  }

  it("skips languages that it can't parse") {
    val xml =
      teiXml(
        languages = List(
          <textLang>Sanskrit</textLang>
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("language ID")
  }

  it("errors on languages that have more than one lang attribute") {
    val xml =
      teiXml(
        languages = List(
          <textLang mainLang="id1" otherLangs="id2">Sanskrit</textLang>
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("language ID")
  }

  it("skips languages without a label") {
    val xml =
      teiXml(
        languages = List(
          <textLang mainLang="he"></textLang>
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should startWith("Missing label for language node")
  }
}
