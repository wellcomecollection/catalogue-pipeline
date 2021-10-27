package weco.pipeline.transformer.tei

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
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

      xml.summary.value shouldBe Some("a manuscript about stuff")
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

      val err = xml.summary
      err shouldBe a[Left[_, _]]
      err.left.value shouldBe a[RuntimeException]
    }
  }

  describe("scribe") {
    it("extracts a single scribe from handNote/persName") {
      val result = new TeiXml(teiXml(id, handNotes = List(handNotes(persNames = List(scribe("Tony Stark")))))).scribes

      result shouldBe List(Contributor(Person("Tony Stark"), List(ContributionRole("scribe"))))
    }
    it("extracts a list of scribes from handNote/persName") {
      val result = new TeiXml(teiXml(id, handNotes = List(handNotes(persNames = List(scribe("Tony Stark"), scribe("Peter Parker"), scribe("Steve Rogers")))))).scribes

      result shouldBe List(Contributor(Person("Tony Stark"), List(ContributionRole("scribe"))),Contributor(Person("Peter Parker"), List(ContributionRole("scribe"))),Contributor(Person("Steve Rogers"), List(ContributionRole("scribe"))))
    }
    it("doesn't extract a contributor from from handNote/persName if it doesn't have role=scr"){
      val result = new TeiXml(teiXml(id, handNotes = List(handNotes(persNames = List(persName("Clark Kent")))))).scribes

      result shouldBe Nil
    }
    it("extracts scribes from handNote with scribe attribute"){
      val result = new TeiXml(teiXml(id, handNotes = List(handNotes(label = "Steve Rogers", scribe = Some("sole")),handNotes(label = "Bruce Banner", scribe = Some("sole"))))).scribes

      result shouldBe List(Contributor(Person("Steve Rogers"), List(ContributionRole("scribe"))),Contributor(Person("Bruce Banner"), List(ContributionRole("scribe"))))
    }

  }

}
