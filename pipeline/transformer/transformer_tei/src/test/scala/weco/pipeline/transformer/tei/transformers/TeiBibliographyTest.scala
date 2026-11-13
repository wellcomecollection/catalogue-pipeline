package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Note, NoteType}

class TeiBibliographyTest extends AnyFunSpec with Matchers {
  private def bibliography(texts: String*): List[Note] =
    texts.toList.map(Note(NoteType.BibliographicalInformation, _))

  it("makes a note for each reference in a manuscript's bibliography") {
    val msDesc =
      <msDesc>
        <additional>
          <listBibl>
            <bibl>Test catalogue, p. 1</bibl>
            <bibl>Another test catalogue <gap reason="omitted"/> no. 2</bibl>
          </listBibl>
        </additional>
      </msDesc>

    TeiBibliography(msDesc) shouldBe bibliography(
      "Test catalogue, p. 1",
      "Another test catalogue no. 2"
    )
  }

  it("reads the bibliography of an item") {
    val msItem =
      <msItem xml:id="item_1">
        <listBibl><bibl>Test author, p. 3</bibl></listBibl>
        <bibl>Test author, p. 4</bibl>
      </msItem>

    TeiBibliography(msItem) shouldBe bibliography(
      "Test author, p. 3",
      "Test author, p. 4"
    )
  }

  it("puts a reference written as fields together as a citation") {
    val msPart =
      <msPart>
        <additional>
          <listBibl>
            <head>Bibliographical abbreviations</head>
            <bibl>
              <title>A test catalogue</title>
              <editor>A. Editor, et al.</editor>
              <placeName>Testville</placeName>
              <publisher>Test Press</publisher>
              <date>1949</date>
              <abbr>TC</abbr>
              <note>Vol. 9, pp. 1-2</note>
            </bibl>
            <bibl>
              <author>A. Author</author>
              <author>B. Author</author>
              <title>Another <hi rend="italic">test</hi> book</title>
              <date>1929</date>
            </bibl>
            <bibl><!-- template --><title>Only a title</title></bibl>
            <bibl><abbr>TB</abbr><publisher>Test Press</publisher></bibl>
            <bibl>
              <author>C. Author</author>
              <editor>D. Editor</editor>
              <title>Edited test works</title>
              <extent>2 vols</extent>
              <ref target="test_ref">Online</ref>
            </bibl>
          </listBibl>
        </additional>
      </msPart>

    TeiBibliography(msPart) shouldBe bibliography(
      "TC: A. Editor, et al. (ed.), A test catalogue (Testville: Test Press, 1949), Vol. 9, pp. 1-2",
      "A. Author and B. Author, Another test book (1929)",
      "Only a title",
      "TB: Test Press",
      "C. Author, D. Editor (ed.), Edited test works, 2 vols, Online"
    )
  }

  it("keeps the text of a reference that has its own punctuation") {
    val msDesc =
      <msDesc>
        <additional>
          <listBibl>
            <bibl><title>A test title</title> (2:594)</bibl>
            <bibl><p>First test reference.</p><p>Second test reference.</p></bibl>
          </listBibl>
        </additional>
      </msDesc>

    TeiBibliography(msDesc) shouldBe bibliography(
      "A test title (2:594)",
      "First test reference. Second test reference."
    )
  }

  it("ignores empty references and bibl outside the bibliography") {
    val msDesc =
      <msDesc>
        <additional>
          <adminInfo>
            <recordHist><source><bibl>A source catalogue</bibl></source></recordHist>
          </adminInfo>
          <listBibl>
            <bibl><!-- insert reference --></bibl>
            <bibl><title/><author/></bibl>
          </listBibl>
        </additional>
      </msDesc>

    TeiBibliography(msDesc) shouldBe Nil
  }
}
