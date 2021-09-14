package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

class TeiXmlTest extends AnyFunSpec with Matchers with TeiGenerators {
  val id = "manuscript_15651"

  it(
    "fails parsing a tei XML if the supplied id is different from the id in the XML"
  ) {
    val suppliedId = "another_id"
    val result = TeiXml(suppliedId, teiXml(id = id).toString())
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include(suppliedId)
    result.left.get.getMessage should include(id)
  }

  it("strips spaces from the id") {
    val result = TeiXml(id, teiXml(id = s" $id").toString())
    result shouldBe a[Right[_, _]]
    result.right.get.id shouldBe id
  }

  it("gets the title from the TEI") {
    val titleString = "This is the title"
    val result =
      TeiXml(id, teiXml(id = id, title = titleElem(titleString)).toString())
        .flatMap(_.title)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe titleString
  }

  it("gets the title from the msItem if there's only one item") {
    val titleString = "This is the title"
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(msItem(s"${id}_1", List(itemTitle(titleString)))),
        title = titleElem("this is not the title")
      ).toString()
    ).flatMap(_.title)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe titleString
  }

  it("doesn't get the title from the item if there's more than one") {
    val titleString = "This is the title"
    val item1 = msItem("id1", List(itemTitle("itemTitle1")))
    val item2 = msItem("id2", List(itemTitle("itemTitle2")))
    val result =
      TeiXml(
        id,
        teiXml(
          id = id,
          title = titleElem(titleString),
          items = List(item1, item2)).toString())
        .flatMap(_.title)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe titleString
  }

  it("picks the title with type=original if there's more than one in the item") {
    val titleString = "This is the title"
    val title = itemTitle("this is not the title")
    val originalTitle = originalItemTitle(titleString)
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(msItem(s"${id}_1", List(title, originalTitle))),
        title = titleElem("this is not the title")
      ).toString()
    ).flatMap(_.title)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe titleString
  }

  it(
    "falls back if there's more than one title in the item and none have type=original") {
    val title = itemTitle("this is not the title")
    val secondTitle = itemTitle("this is not the title either")
    val titleString = "this is the title"
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(msItem(s"${id}_1", List(title, secondTitle))),
        title = titleElem(titleString)
      ).toString()
    ).flatMap(_.title)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe titleString
  }

  it("falls back if there's more than one title with type=original") {
    val firstItemTitle = originalItemTitle("this is not the title")
    val secondItemTitle = originalItemTitle("this is not the title either")
    val validTitle = "this is the title"
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(msItem(s"${id}_1", List(firstItemTitle, secondItemTitle))),
        title = titleElem(validTitle)
      ).toString()
    ).flatMap(_.title)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe validTitle
  }

  it("fails if there are more than one title node") {
    val titleString1 = "This is the first title"
    val titleString2 = "This is the second title"
    val titleStm =
      <titleStmt>
        <title>{titleString1}</title>
        <title>{titleString2}</title>
      </titleStmt>
    val result =
      TeiXml(id, teiXml(id = id, title = titleStm).toString()).flatMap(_.title)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("title")
  }

  it("extracts internal works"){
    val firstItemTitle = "this is first item title"
    val secondItemTitle = "this is second item title"
    val firstItemId = s"${id}_1"
    val firstItem = msItem(firstItemId, List(originalItemTitle(firstItemTitle)))
    val secondItemId = s"${id}_2"
    val secondItem = msItem(secondItemId, List(originalItemTitle(secondItemTitle)))
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(firstItem, secondItem)
      ).toString()
    ).flatMap(_.nestedTeiData)

    result shouldBe a[Right[_,_]]
    result.right.get shouldBe Seq(TeiData(id = firstItemId, title= firstItemTitle), TeiData(id = secondItemId, title = secondItemTitle))
  }

}
