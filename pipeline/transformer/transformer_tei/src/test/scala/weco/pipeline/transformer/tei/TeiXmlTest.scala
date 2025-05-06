package weco.pipeline.transformer.tei

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.generators.LabelDerivedIdentifiersGenerators
import weco.pipeline.transformer.tei.generators.TeiGenerators
import weco.sierra.generators.SierraIdentifierGenerators

import scala.xml.Elem

class TeiXmlTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with SierraIdentifierGenerators
    with TeiGenerators
    with LabelDerivedIdentifiersGenerators {
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
      TeiXml(id, teiXml(id = id, refNo = idnoMsId(titleString)).toString())
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
        refNo = idnoMsId(topLevelTitle)
      ).toString()
    ).flatMap(_.parse)

    result.value.title shouldBe topLevelTitle
  }

  it("fails if there are more than one title node") {
    val titleString1 = "This is the first title"
    val titleString2 = "This is the second title"
    val titleStm = {
      <idno type="msID">{titleString1}</idno>
      <idno type="msID">{titleString2}</idno>
    }
    val result =
      TeiXml(id, teiXml(id = id, refNo = titleStm).toString()).flatMap(_.parse)
    result shouldBe a[Left[_, _]]
    result.left.get.getMessage should include("title")
  }

  describe("bNumber") {
    it("parses a tei xml and returns TeiData with bNumber") {
      val bnumber = createSierraBibNumber.withCheckDigit

      TeiXml(
        id,
        teiXml(id = id, identifiers = Some(sierraIdentifiers(bnumber)))
          .toString()
      ).flatMap(_.parse).value.bNumber shouldBe Some(bnumber)
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

    it("retains paragraph tags in the summary") {
      val description =
        """a <pppp/><note>delightful <p>manu<xp></xp>script</p></note> <p /><p/> about <p>stuff</p><pb />"""

      val xml = TeiXml(
        id,
        teiXml(id = id, summary = Some(summary(description)))
          .toString()
      ).flatMap(_.parse)

      xml.value.description shouldBe Some(
        "a delightful <p>manuscript</p> <p /><p/> about <p>stuff</p>"
      )
    }

    it("discards paragraph attributes from the summary") {
      val description =
        """a <note>delightful <p hand="reza_abbasi">manu<xp></xp>script</p></note> <p a="b" /> <p a="b" c="d"/> about stuff"""

      val xml = TeiXml(
        id,
        teiXml(id = id, summary = Some(summary(description)))
          .toString()
      ).flatMap(_.parse)

      xml.value.description shouldBe Some(
        "a delightful <p>manuscript</p> <p/> <p/> about stuff"
      )
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
        physDesc = Some(
          physDesc(handNotes =
            List(
              handNotes(persNames = List(scribe("Tony Stark"))),
              handNotes(persNames = List(scribe("Peter Parker"))),
              handNotes(persNames = List(scribe("Steve Rogers")))
            )
          )
        )
      )
    ).parse

    result.value.contributors shouldBe List(
      Contributor(
        agent = Person(
          label = "Tony Stark",
          id = labelDerivedPersonIdentifier("tony stark")
        ),
        roles = List(ContributionRole("scribe"))
      ),
      Contributor(
        agent = Person(
          label = "Peter Parker",
          id = labelDerivedPersonIdentifier("peter parker")
        ),
        roles = List(ContributionRole("scribe"))
      ),
      Contributor(
        agent = Person(
          label = "Steve Rogers",
          id = labelDerivedPersonIdentifier("steve rogers")
        ),
        roles = List(ContributionRole("scribe"))
      )
    )
  }

  it("extracts the origin for the wrapper work") {
    val result = new TeiXml(
      teiXml(
        id,
        history =
          Some(history(origPlace = Some(origPlace(country = Some("India")))))
      )
    ).parse

    result.value.origin shouldBe List(
      ProductionEvent(
        "India",
        places = List(Place("India")),
        agents = Nil,
        dates = Nil
      )
    )
  }

  it("extracts material description for the wrapper work") {
    val result = new TeiXml(
      teiXml(
        id,
        physDesc = Some(
          physDesc(objectDesc =
            Some(
              objectDesc(
                None,
                support = Some(
                  support("Multiple manuscript parts collected in one volume.")
                )
              )
            )
          )
        )
      )
    ).parse

    result.value.physicalDescription shouldBe Some(
      "Multiple manuscript parts collected in one volume."
    )
  }

  it("extracts subjects for the wrapper work") {
    val result = new TeiXml(
      teiXml(
        id,
        profileDesc = Some(
          profileDesc(keywords =
            List(keywords(subjects = List(subject("Botany"))))
          )
        )
      )
    ).parse

    result.value.subjects shouldBe List(
      Subject(
        id = labelDerivedConceptIdentifier("botany"),
        label = "Botany",
        concepts = List(Concept(label = "Botany"))
      )
    )
  }

  it("adds hand notes") {
    val id = "id"
    val result = new TeiXml(
      teiXml(
        id,
        physDesc = Some(
          physDesc(handNotes =
            List(
              handNotes(label = "neatly written text"),
              handNotes(label = "even more neatly written text")
            )
          )
        )
      )
    ).parse

    result.value.notes shouldBe List(
      Note(NoteType.HandNote, "neatly written text"),
      Note(NoteType.HandNote, "even more neatly written text")
    )
  }
}
