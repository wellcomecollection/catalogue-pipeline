package weco.pipeline.transformer.tei

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState.Unidentifiable
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{ContributionRole, Contributor, Person}
import weco.pipeline.transformer.tei.generators.TeiGenerators
import weco.sierra.generators.SierraIdentifierGenerators

import scala.xml.Elem

class TeiXmlTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with SierraIdentifierGenerators
    with TeiGenerators {
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

  it("gets the top level title even if there's only one item") {
    val theItemTitle = "This is the item title"
    val topLevelTitle = "This is the top-level title"

    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(msItem(s"${id}_1", List(itemTitle(theItemTitle)))),
        title = titleElem(topLevelTitle)
      ).toString()
    ).flatMap(_.title)

    result.value shouldBe topLevelTitle
  }

  it("fails if there are more than one title node") {
    val titleString1 = "This is the first title"
    val titleString2 = "This is the second title"
    val titleStm = { <idno type="msID">{titleString1}</idno>
      <idno type="msID">{titleString2}</idno> }
    val result =
      TeiXml(id, teiXml(id = id, title = titleStm).toString()).flatMap(_.title)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("title")
  }

  describe("bNumber") {
    it("parses a tei xml and returns TeiData with bNumber") {
      val bnumber = createSierraBibNumber.withCheckDigit

      TeiXml(
        id,
        teiXml(id = id, identifiers = Some(sierraIdentifiers(bnumber)))
          .toString()).value.bNumber.value shouldBe Some(bnumber)
    }

    it("fails if there's more than one b-number in the XML") {
      val bnumber = createSierraBibNumber.withCheckDigit

      val xmlValue: Elem = <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
        <teiHeader>
          <fileDesc>
            <sourceDesc>
              <msDesc xml:lang="en" xml:id="MS_Arabic_1">
                <msIdentifier>
                  {sierraIdentifiers(bnumber)}
                  {sierraIdentifiers(bnumber)}
                </msIdentifier>
                <msContents>
                </msContents>
              </msDesc>
            </sourceDesc>
          </fileDesc>
        </teiHeader>
      </TEI>

      val xml = new TeiXml(xmlValue)

      val err = xml.bNumber
      err shouldBe a[Left[_, _]]
      err.left.value shouldBe a[RuntimeException]
    }
  }

  describe("summary") {
    it("removes XML tags from the summary") {
      val description = "a <note>manuscript</note> about stuff"

      val xml = TeiXml(
        id,
        teiXml(id = id, summary = Some(summary(description)))
          .toString()
      ).value

      xml.summary().value shouldBe Some("a manuscript about stuff")
    }

    it("fails parsing if there's more than one summary node") {
      val bnumber = createSierraBibNumber.withCheckDigit

      val xml = new TeiXml(
        <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
          <teiHeader>
            <fileDesc>
              <sourceDesc>
                <msDesc xml:lang="en" xml:id="MS_Arabic_1">
                  <msContents>
                    {summary(bnumber)}
                    {summary(bnumber)}
                  </msContents>
                </msDesc>
              </sourceDesc>
            </fileDesc>
          </teiHeader>
        </TEI>
      )

      val err = xml.summary()
      err shouldBe a[Left[_, _]]
      err.left.value shouldBe a[RuntimeException]
    }
  }

  it("extracts internal works") {
    val firstItemTitle = "this is first item title"
    val secondItemTitle = "this is second item title"
    val firstItemId = s"${id}_1"
    val firstItem = msItem(firstItemId, List(originalItemTitle(firstItemTitle)))
    val secondItemId = s"${id}_2"
    val secondItem =
      msItem(secondItemId, List(originalItemTitle(secondItemTitle)))
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(firstItem, secondItem)
      ).toString()
    ).flatMap(_.nestedTeiData)

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
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(firstItem)
      ).toString()
    ).flatMap(_.nestedTeiData)

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
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        title = titleElem("Wrapper title"),
        items = List(firstItem)
      ).toString()
    ).flatMap(_.nestedTeiData)

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
    val result = for {
      parsed <- TeiXml(id, teiXml(id = id, items = List(firstItem)).toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData.head.languages

    result shouldBe a[Right[_, _]]
    result.value shouldBe List(Language(id = "san", label = "Sanskrit"))
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

    val result = for {
      parsed <- TeiXml(id, xml.toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData
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

    val result = for {
      parsed <- TeiXml(id, xml.toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData
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

    val result = for {
      parsed <- TeiXml(id, xml.toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData
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

    val result = for {
      parsed <- TeiXml(id, xml.toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData
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
    val result = for {
      parsed <- TeiXml(id, xml.toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData
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
    val result = for {
      parsed <- TeiXml(id, xml.toString())
      nestedData <- parsed.nestedTeiData
    } yield nestedData
    result shouldBe a[Right[_, _]]
    result.value shouldBe List(
      TeiData(
        id = "1",
        title = s"$wrapperTitle part 1",
        nestedTeiData = Nil
      ))
  }

  it("extracts author from msItem"){
    val result = TeiXml(
      id,
      teiXml(
        id = id,
        items = List(msItem(s"${id}_1", authors = List(author(label = "Terry Pratchett")))),
      ).toString()
    ).flatMap(_.nestedTeiData)

    result shouldBe a[Right[_, _]]
    result.value.head.authors shouldBe List(Contributor(Unidentifiable, Person("Terry Pratchett"), List(ContributionRole("author"))))
  }
}
