package weco.pipeline.transformer.tei.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.tei.TeiData
import weco.pipeline.transformer.tei.generators.TeiGenerators

class TeiNestedDataTest extends AnyFunSpec with TeiGenerators with Matchers with EitherValues{
  val id = "manuscript_15651"
  val wrapperTitle = "root title"

  it("extracts internal works") {
    val firstItemTitle = "this is first item title"
    val secondItemTitle = "this is second item title"
    val firstItemId = s"${id}_1"
    val firstItem = msItem(firstItemId, List(originalItemTitle(firstItemTitle)))
    val secondItemId = s"${id}_2"
    val secondItem =
      msItem(secondItemId, List(originalItemTitle(secondItemTitle)))
    val result = TeiNestedData.nestedTeiData(teiXml(
        id = id,
        items = List(firstItem, secondItem)
      ),wrapperTitle)


    result shouldBe a[Right[_, _]]
    result.right.get shouldBe Seq(
      TeiData(id = firstItemId, title = firstItemTitle),
      TeiData(id = secondItemId, title = secondItemTitle))
  }

  it(
    "extracts the original title for internal items if there is more than one title") {
    val firstItemTitle = "this is original item title"
    val secondItemTitle = "this is second item title"
    val itemId = s"${id}_1"
    val firstItem = msItem(
      itemId,
      List(originalItemTitle(firstItemTitle), itemTitle(secondItemTitle)))
    val result = TeiNestedData.nestedTeiData(teiXml(
        id = id,
        items = List(firstItem)
      ), wrapperTitle)

    result shouldBe a[Right[_, _]]
    result.value shouldBe Seq(TeiData(id = itemId, title = firstItemTitle))
  }

  it(
    "constructs the title if there are multiple titles and none is marked as original") {
    val firstItemTitle = "this is first item title"
    val secondItemTitle = "this is second item title"
    val itemId = s"${id}_1"
    val firstItem = msItem(
      itemId,
      List(itemTitle(firstItemTitle), itemTitle(secondItemTitle)))
    val wrapperTitle = "Wrapper title"
    val result = TeiNestedData.nestedTeiData(teiXml(
        id = id,
        title = titleElem(wrapperTitle),
        items = List(firstItem)
    ), wrapperTitle)

    result shouldBe a[Right[_, _]]
    result.value shouldBe Seq(
      TeiData(id = itemId, title = "Wrapper title item 1"))
  }

  it("can parse language in items") {
    val id = "id1"
    val firstItem = msItem(
      id,
      List(originalItemTitle("")),
      List(mainLanguage("sa", "Sanskrit")))
    val result = TeiNestedData.nestedTeiData(teiXml(id = id, items = List(firstItem)), wrapperTitle)

    result shouldBe a[Right[_, _]]
    result.value.head.languages shouldBe List(Language(id = "san", label = "Sanskrit"))
  }

  it("can extract nested data from msPart") {
    val description = "this is the part description"
    val wrapperTitle = "test title"
    val number = 1
    val xml = teiXml(
      id = id,
      title = titleElem(wrapperTitle),
      parts = List(
        msPart(
          id = "1",
          summary = Some(summary(description)),
          languages = List(mainLanguage("ar", "Arabic"))))
    )

    val result =TeiNestedData.nestedTeiData(xml,wrapperTitle)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle part $number",
        description = Some(description),
        languages = List(Language("ara", "Arabic"))))
  }

  it("can extract items within a part") {
    val description = "this is the part description"
    val wrapperTitle = "test title"
    val number = 1
    val innerItem1Id = "part_1_item_1"
    val innerItem1Title = "this is the first inner item title"
    val firstInnerItem = msItem(
      innerItem1Id,
      titles = List(itemTitle(innerItem1Title)),
      languages = Nil,
      items = Nil)
    val innerItem2Id = "part_1_item_2"
    val innerItem2Title = "this is the second inner item title"
    val secondInnerItem = msItem(
      innerItem2Id,
      titles = List(itemTitle(innerItem2Title)),
      languages = Nil,
      items = Nil)
    val xml = teiXml(
      id = id,
      title = titleElem(wrapperTitle),
      parts = List(
        msPart(
          id = "1",
          summary = Some(summary(description)),
          languages = List(mainLanguage("ar", "Arabic")),
          items = List(firstInnerItem, secondInnerItem)
        ))
    )

    val result =TeiNestedData.nestedTeiData(xml,wrapperTitle)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle part $number",
        description = Some(description),
        languages = List(Language("ara", "Arabic")),
        nestedTeiData = List(
          TeiData(id = innerItem1Id, title = innerItem1Title),
          TeiData(id = innerItem2Id, title = innerItem2Title))
      ))
  }

  it(
    "builds the title for items within an msPart if they don't have an explicit one") {
    val description = "this is the part description"
    val wrapperTitle = "test title"
    val number = 1
    val innerItem1Id = "part_1_item_1"
    val firstInnerItem = msItem(innerItem1Id, languages = Nil, items = Nil)
    val xml = teiXml(
      id = id,
      title = titleElem(wrapperTitle),
      parts = List(
        msPart(
          id = "1",
          summary = Some(summary(description)),
          languages = List(mainLanguage("ar", "Arabic")),
          items = List(firstInnerItem)
        ))
    )

    val result =TeiNestedData.nestedTeiData(xml,wrapperTitle)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle part $number",
        description = Some(description),
        languages = List(Language("ara", "Arabic")),
        nestedTeiData = List(
          TeiData(
            id = innerItem1Id,
            title = s"$wrapperTitle part $number item 1"))
      ))
  }

  it("extracts msItems within msItems") {
    val wrapperTitle = "test title"
    val xml = teiXml(
      id = id,
      title = titleElem(wrapperTitle),
      items = List(
        msItem(
          id = "1",
          items = List(
            msItem(
              id = "11",
              titles = List(itemTitle("inner item title")),
              languages = List(mainLanguage("ar", "Arabic"))),
            msItem(id = "12"))
        ))
    )

    val result =TeiNestedData.nestedTeiData(xml,wrapperTitle)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle item 1",
        nestedTeiData = List(
          TeiData(
            id = "11",
            title = "inner item title",
            languages = List(Language("ara", "Arabic"))),
          TeiData(id = "12", title = s"$wrapperTitle item 1 item 2")
        )
      ))
  }

  it(
    "doesn't extract lower level nested data from items for manuscripts in the Fihrist catalogue") {
    val wrapperTitle = "test title"
    val xml = teiXml(
      id = id,
      title = titleElem(wrapperTitle),
      items = List(
        msItem(
          id = "1",
          items = List(
            msItem(
              id = "11",
              titles = List(itemTitle("inner item title")),
              languages = List(mainLanguage("ar", "Arabic"))),
            msItem(id = "12"))
        )),
      catalogues =
        List(catalogueElem("Fihrist"), catalogueElem("Another catalogue"))
    )
    val result =TeiNestedData.nestedTeiData(xml,wrapperTitle)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle item 1",
        nestedTeiData = Nil
      ))
  }

  it(
    "doesn't extract lower level nested data from parts for manuscripts in the Fihrist catalogue") {
    val wrapperTitle = "test title"
    val xml = teiXml(
      id = id,
      title = titleElem(wrapperTitle),
      parts = List(
        msPart(
          id = "1",
          items = List(
            msItem(
              id = "11",
              titles = List(itemTitle("inner item title")),
              languages = List(mainLanguage("ar", "Arabic"))),
            msItem(id = "12"))
        )),
      catalogues =
        List(catalogueElem("Fihrist"), catalogueElem("Another catalogue"))
    )
    val result =TeiNestedData.nestedTeiData(xml,wrapperTitle)
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle part 1",
        nestedTeiData = Nil
      ))
  }
  it("extracts author from msItem"){
    val result = TeiNestedData.nestedTeiData(teiXml(
        id = id,
        items = List(msItem(s"${id}_1", authors = List(author(label = "John Wick")))),
      ),wrapperTitle)

    result shouldBe a[Right[_, _]]
    result.value.head.authors shouldBe List(Contributor(Person("John Wick"), List(ContributionRole("author"))))
  }

  it("if the manuscript is part of the Fihrist catalogue, it extracts authors ids as the fihrist identifier type"){
    val result = TeiNestedData.nestedTeiData(teiXml(
        id = id,
        items = List(msItem(s"${id}_1", authors = List(author(persNames = List(
          persName(label = "Sarah Connor",key = Some("12345"))), key = None)))),
        catalogues = List(catalogueElem("Fihrist"))
      ), wrapperTitle)

    result shouldBe a[Right[_, _]]
    result.value.head.authors shouldBe List(Contributor(Person(label = "Sarah Connor", id = Identifiable(SourceIdentifier(IdentifierType.Fihrist, "Person", "12345"))), List(ContributionRole("author"))))
  }
}
