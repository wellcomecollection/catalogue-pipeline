package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

import scala.xml.{Elem, XML}

class TeiLanguagesTest extends AnyFunSpec with Matchers with EitherValues with TeiGenerators {
  it("gets a single language from the TEI") {
    val xml: Elem =
      teiXml(
        languages = List(
          XML.loadString(
            s"""
               |<textLang mainLang="sa" source="IANA">Sanskrit</textLang>
               |""".stripMargin
          )
        )
      )

    val languages = TeiLanguages(xml)

    languages shouldBe a[Right[_, _]]
    languages.value shouldBe List(Language(id = "sa", label = "Sanskrit"))
  }

  it("gets multiple languages from tei") {
    val xml: Elem =
      teiXml(
        languages = List(
          XML.loadString(
            s"""
               |<textLang mainLang="sa" source="IANA">Sanskrit</textLang>
               |""".stripMargin
          ),
          XML.loadString(
            s"""
               |<textLang otherLangs="la" source="IANA">Latin</textLang>
               |""".stripMargin
          )
        )
      )

    val languages = TeiLanguages(xml)

    languages shouldBe a[Right[_, _]]
    languages.value shouldBe List(
      Language(id = "sa", label = "Sanskrit"),
      Language(id = "la", label = "Latin")
    )
  }

  it("fails if it cannot parse the language id") {
    val xml =
      teiXml(
        languages = List(
          XML.loadString("""<textLang>Sanskrit</textLang>""")
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("language id")
  }

  it("fails if it there is more than one language id attribute") {
    val xml =
      teiXml(
        languages = List(
          XML.loadString("""<textLang mainLang="id1" otherLangs="id2">Sanskrit</textLang>""")
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("language id")
  }
}
