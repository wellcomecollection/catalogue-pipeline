package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
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
    "gets the title from the TEI") {
    val titleString = "This is the title"
    val result = TeiXml(id, teiXml(id = id, title= Some(title(titleString))).toString()).flatMap(_.title)
    result shouldBe a[Right[_,_]]
    result.right.get shouldBe Some(titleString)
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
