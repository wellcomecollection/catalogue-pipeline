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

  it("reads every language on a node that has both mainLang and otherLangs") {
    val xml =
      teiXml(
        languages = List(
          <textLang mainLang="grc" otherLangs="el">Greek</textLang>
        )
      )

    TeiLanguages(xml).value shouldBe ((
      List(
        Language(id = "grc", label = "Greek, Ancient (to 1453)"),
        Language(id = "gre", label = "Greek, Modern (1453- )")
      ),
      Nil
    ))
  }

  it("keeps the language of the id that maps when another id does not") {
    val xml =
      teiXml(
        languages = List(
          <textLang mainLang="ar" otherLangs="ota">Arabic</textLang>
        )
      )

    TeiLanguages(xml).value shouldBe ((
      List(Language(id = "ara", label = "Arabic")),
      Nil
    ))
  }

  it("keeps the main language when otherLangs holds several ids") {
    val xml =
      teiXml(
        languages = List(
          <textLang mainLang="la" otherLangs="grc fr">Latin</textLang>
        )
      )

    TeiLanguages(xml).value shouldBe ((
      List(Language(id = "lat", label = "Latin")),
      Nil
    ))
  }

  it(
    "puts a multi-language node in a language note when its label names no single language"
  ) {
    val xml =
      teiXml(
        languages = List(
          <textLang mainLang="ar" otherLangs="fa">Arabic and Persian</textLang>
        )
      )

    val result = TeiLanguages(xml)

    result shouldBe a[Right[_, _]]
    result.value shouldBe ((
      Nil,
      List(Note(NoteType.LanguageNote, "Arabic and Persian"))
    ))
  }

  it("does not fail the whole manuscript for a multi-language node") {
    val xml =
      teiXml(
        languages = List(
          mainLanguage("sa", "Sanskrit"),
          <textLang mainLang="ar" otherLangs="es">Arabic with interlinear Spanish</textLang>
        )
      )

    TeiLanguages(xml).value shouldBe ((
      List(Language(id = "san", label = "Sanskrit")),
      List(
        Note(NoteType.LanguageNote, "Arabic with interlinear Spanish")
      )
    ))
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
