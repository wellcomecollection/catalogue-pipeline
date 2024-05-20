package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.generators.TeiGenerators

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

    TeiLanguages(xml).value shouldBe ((
      List(Language(id = "san", label = "Sanskrit")),
      Nil
    ))
  }

  it("gets multiple languages from TEI") {
    val xml: Elem =
      teiXml(
        languages = List(
          mainLanguage("sa", "Sanskrit"),
          otherLanguage("la", "Latin")
        )
      )

    TeiLanguages(xml).value shouldBe ((
      List(
        Language(id = "san", label = "Sanskrit"),
        Language(id = "lat", label = "Latin")
      ),
      Nil
    ))
  }

  it("puts languages without an id in a language note") {
    val xml =
      teiXml(
        languages = List(
          <textLang>Sanskrit</textLang>
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Right[_, _]]
    result.value shouldBe ((Nil, List(Note(NoteType.LanguageNote, "Sanskrit"))))
  }

  it("puts languages with a label it can't match in a language note") {
    val xml =
      teiXml(
        languages = List(
          mainLanguage("sa", "Sanskrit mainly")
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Right[_, _]]
    result.value shouldBe ((
      Nil,
      List(Note(NoteType.LanguageNote, "Sanskrit mainly"))
    ))
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

    TeiLanguages(xml) shouldBe Right((List(), List()))
  }

  it("skips languages without a label and without an id") {
    val xml =
      teiXml(
        languages = List(
          <textLang></textLang>
        )
      )

    TeiLanguages(xml) shouldBe Right((List(), List()))
  }
}
