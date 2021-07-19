package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.tei.TeiXml
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

class TeiLanguagesTest extends AnyFunSpec with Matchers with TeiGenerators {
  val id = "manuscript_15651"

  it("gets a single language from the TEI") {
    val languageLabel = "Sanskrit"
    val languageId = "sa"
    val result = TeiXml(
      id,
      teiXml(id = id, languages = List(mainLanguage(languageId, languageLabel)))
        .toString()
    ).flatMap(TeiLanguages(_))
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe List(Language(languageId, languageLabel))
  }

  it("gets multiple languages from tei") {
    val languageLabel1 = "Sanskrit"
    val languageId1 = "sa"
    val languageLabel2 = "Latin"
    val languageId2 = "la"
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        languages = List(
          mainLanguage(languageId1, languageLabel1),
          otherLanguage(languageId2, languageLabel2)
        )
      ).toString()
    ).flatMap(TeiLanguages(_))
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe List(
      Language(languageId1, languageLabel1),
      Language(languageId2, languageLabel2)
    )
  }

  it("fails if it cannot parse the language id") {
    val languageLabel = "Sanskrit"
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        languages = List(<textLang>{
          languageLabel
          }</textLang>)).toString()).flatMap(TeiLanguages(_))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("language id")
  }

  it("fails if it there is more than one language id attribute") {
    val languageLabel = "Sanskrit"
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        languages = List(<textLang mainLang="id1" otherLangs="id2">{
          languageLabel
          }</textLang>)
      ).toString()
    ).flatMap(TeiLanguages(_))
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("language id")
  }
}
