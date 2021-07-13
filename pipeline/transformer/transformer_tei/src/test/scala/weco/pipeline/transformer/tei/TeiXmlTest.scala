package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

class TeiXmlTest extends AnyFunSpec with Matchers with TeiGenerators {
  val id = "manuscript_15651"

  it(
    "fails parsing a tei XML if the supplied id is different from the id in the XML") {
    val suppliedId = "another_id"
    val result = TeiXml(suppliedId, teiXml(id = id).toString())
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include(suppliedId)
    result.left.get.getMessage should include(id)
  }

  it(
    "strips spaces from the id") {
    val result = TeiXml(id, teiXml(id = s" $id").toString())
    result shouldBe a[Right[_, _]]
    result.right.get.id shouldBe id
  }

  it(
    "gets the title from the TEI") {
    val titleString = "This is the title"
    val result = TeiXml(id, teiXml(id = id, title= Some(title(titleString))).toString()).flatMap(_.title)
    result shouldBe a[Right[_,_]]
    result.right.get shouldBe Some(titleString)
  }

  it(
    "gets a single language from the TEI") {
    val languageLabel = "Sanskrit"
    val languageId = "sa"
    val result = TeiXml(id, teiXml(id = id, languages = List(mainLanguage(languageId,languageLabel))).toString()).flatMap(_.languages)
    result shouldBe a[Right[_,_]]
    result.right.get shouldBe List(Language(languageId, languageLabel))
  }

  it(
    "gets multiple languages from tei") {
    val languageLabel1 = "Sanskrit"
    val languageId1 = "sa"
    val languageLabel2 = "Latin"
    val languageId2 = "la"
    val result = TeiXml(id, teiXml(id = id, languages = List(mainLanguage(languageId1,languageLabel1), otherLanguage(languageId2, languageLabel2))).toString()).flatMap(_.languages)
    result shouldBe a[Right[_,_]]
    result.right.get shouldBe List(Language(languageId1, languageLabel1), Language(languageId2, languageLabel2))
  }

  it(
    "fails if it cannot parse the language id") {
    val languageLabel = "Sanskrit"
    val result = TeiXml(id, teiXml(id = id, languages = List(<textLang>{languageLabel}</textLang>)).toString()).flatMap(_.languages)
    result shouldBe a[Left[_,_]]
    result.left.get.getMessage should include ("language id")
  }

  it(
    "fails if it there is more than one language id attribute") {
    val languageLabel = "Sanskrit"
    val result = TeiXml(id, teiXml(id = id, languages = List(<textLang mainLang="id1" otherLangs="id2">{languageLabel}</textLang>)).toString()).flatMap(_.languages)
    result shouldBe a[Left[_,_]]
    result.left.get.getMessage should include ("language id")
  }

  it(
    "fails if there are more than one title node") {
    val titleString1 = "This is the first title"
    val titleString2 = "This is the second title"
    val titleStm =
      <titleStmt>
        <title>{titleString1}</title>
        <title>{titleString2}</title>
      </titleStmt>
    val result = TeiXml(id, teiXml(id = id, title= Some(titleStm)).toString()).flatMap(_.title)
    result shouldBe a[Left[_,_]]
    result.left.get.getMessage should include ("title")
  }

}
