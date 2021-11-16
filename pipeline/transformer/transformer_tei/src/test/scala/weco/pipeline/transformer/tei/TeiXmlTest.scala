package weco.pipeline.transformer.tei

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.tei.generators.TeiGenerators
import weco.pipeline.transformer.transformers.ParsedPeriod
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
        .flatMap(_.parse)
    result shouldBe a[Right[_, _]]
    result.value.title shouldBe titleString
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
    ).flatMap(_.parse)

    result.value.title shouldBe topLevelTitle
  }

  it("fails if there are more than one title node") {
    val titleString1 = "This is the first title"
    val titleString2 = "This is the second title"
    val titleStm = { <idno type="msID">{titleString1}</idno>
      <idno type="msID">{titleString2}</idno> }
    val result =
      TeiXml(id, teiXml(id = id, title = titleStm).toString()).flatMap(_.parse)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("title")
  }

  describe("bNumber") {
    it("parses a tei xml and returns TeiData with bNumber") {
      val bnumber = createSierraBibNumber.withCheckDigit

      TeiXml(
        id,
        teiXml(id = id, identifiers = Some(sierraIdentifiers(bnumber)))
          .toString()).flatMap(_.parse).value.bNumber shouldBe Some(bnumber)
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

      val err = xml.parse
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
      ).flatMap(_.parse)

      xml.value.description shouldBe Some("a manuscript about stuff")
    }

    it("fails parsing if there's more than one summary node") {
      val bnumber = createSierraBibNumber.withCheckDigit

      val err = new TeiXml(
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
      ).parse

      err shouldBe a[Left[_, _]]
      err.left.value shouldBe a[RuntimeException]
    }
  }

  it("extracts a list of scribes from handNote/persName") {
    val result = new TeiXml(
      teiXml(
        id,
        physDesc = Some(physDesc(handNotes = List(
          handNotes(persNames = List(scribe("Tony Stark"))),
          handNotes(persNames = List(scribe("Peter Parker"))),
          handNotes(persNames = List(scribe("Steve Rogers"))))))

      )).parse

    result.value.contributors shouldBe List(
      Contributor(Person("Tony Stark"), List(ContributionRole("scribe"))),
      Contributor(Person("Peter Parker"), List(ContributionRole("scribe"))),
      Contributor(Person("Steve Rogers"), List(ContributionRole("scribe")))
    )
  }
  describe("origin") {
    it("extracts the origin country") {
      val result = new TeiXml(
        teiXml(
          id,
          origPlace = Some(origPlace(country = Some("India")))
        )).parse

      result.value.origin shouldBe List(
        ProductionEvent(
          "India",
          places = List(Place("India")),
          agents = Nil,
          dates = Nil))
    }

    it("extracts the origin - country region and settlement") {
      val result = new TeiXml(
        teiXml(
          id,
          origPlace = Some(
            origPlace(
              country = Some("United Kingdom"),
              region = Some("England"),
              settlement = Some("London")))
        )).parse

      val label = "United Kingdom, England, London"
      result.value.origin shouldBe List(
        ProductionEvent(
          label,
          places = List(Place(label)),
          agents = Nil,
          dates = Nil))
    }

    it("ignores text not in country region or settlement") {
      val result = new TeiXml(
        teiXml(
          id,
          origPlace = Some(
            origPlace(
              country = Some("United Kingdom"),
              region = Some("England"),
              settlement = Some("London"),
              label = Some("stuff")))
        )).parse

      val label = "United Kingdom, England, London"
      result.value.origin shouldBe List(
        ProductionEvent(
          label,
          places = List(Place(label)),
          agents = Nil,
          dates = Nil))
    }

    it("returns an agent if there is an orgName") {
      val result = new TeiXml(
        teiXml(
          id,
          origPlace = Some(
            origPlace(
              country = Some("Egypt"),
              settlement = Some("Wadi El Natrun"),
              orgName = Some("Monastery of St Macarius the Great")))
        )).parse

      val label = "Egypt, Wadi El Natrun"
      result.value.origin shouldBe List(
        ProductionEvent(
          label,
          places = List(Place(label)),
          agents = List(Organisation("Monastery of St Macarius the Great")),
          dates = Nil))
    }

    it("returns a date") {
      val result = new TeiXml(
        teiXml(
          id,
          originDates = List(originDate("Gregorian", "1756"))
        )).parse

      result.value.origin shouldBe List(
        ProductionEvent(
          label = "1756",
          places = Nil,
          agents = Nil,
          dates = List(ParsedPeriod("1756"))))
    }

    it("doesn't return a date if the calendar is not Gregorian") {
      val result = new TeiXml(
        teiXml(
          id,
          originDates = List(originDate("hijri", "5 Ramaḍān 838 (part 1)"))
        )).parse

      result.value.origin shouldBe Nil
    }

    it("ignores non text nodes in the label") {
      val result = new TeiXml(
        teiXml(
          id,
          originDates = List(<origDate calendar="gregorian">ca.1732-63AD
          <note>from watermarks</note>
        </origDate>)
        )).parse
      result.value.origin shouldBe List(
        ProductionEvent(
          label = "ca.1732-63AD",
          places = Nil,
          agents = Nil,
          dates = List(ParsedPeriod("ca.1732-63AD"))))
    }
  }

  it("extracts material description for the wrapper work"){
    val result = new TeiXml(
      teiXml(
        id,
        physDesc = Some(physDesc(objectDesc = Some(objectDesc(None,
          support = Some(support("Multiple manuscript parts collected in one volume."))))))
      )).parse

    result.value.physicalDescription shouldBe Some("Multiple manuscript parts collected in one volume.")
  }

  it("extracts subjects for the wrapper work"){
    val result = new TeiXml(
      teiXml(
        id,
        profileDesc = Some(profileDesc(keywords = List(keywords(subjects = List(subject("Botany"))))))      )).parse

    result.value.subjects shouldBe List(Subject(label = "Botany", concepts = List(Concept(label ="Botany"))))
  }
}
