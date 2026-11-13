package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.generators.TeiGenerators

import scala.xml.Elem

class TeiNotesTest extends AnyFunSpec with Matchers with TeiGenerators {
  it("finds a single colophon note") {
    val xml: Elem =
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="TeiNotes_Example_1">
        <teiHeader>
          <fileDesc>
            <sourceDesc>
              <msDesc xml:lang="en" xml:id="TeiNotes_Example_1">
                <msContents>
                  <colophon facs="#i0033">
                    <locus>f. 31v.21</locus> وليكن هذا آخر الكلام والحمد لله على التمام </colophon>
                </msContents>
              </msDesc>
            </sourceDesc>
          </fileDesc>
        </teiHeader>
      </TEI>

    TeiNotes(xml) shouldBe List(
      Note(
        contents = "f. 31v.21 وليكن هذا آخر الكلام والحمد لله على التمام",
        noteType = NoteType.ColophonNote
      )
    )
  }

  it("removes newlines in the colophon note") {
    // e.g. Indic/G.32.d.xml
    val xml: Elem =
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="Wellcome_G_32_d">
        <teiHeader>
          <fileDesc>
            <sourceDesc>
              <msDesc xml:lang="en" xml:id="Wellcome_G_32_d">
                <msContents>
                  <colophon>
                    <locus>F.8v </locus>iti śastraparikṣādhyayane ṣaṣṭamoddeśaḥ
                    6.</colophon>
                </msContents>
              </msDesc>
            </sourceDesc>
          </fileDesc>
        </teiHeader>
      </TEI>

    TeiNotes(xml) shouldBe List(
      Note(
        contents = "F.8v iti śastraparikṣādhyayane ṣaṣṭamoddeśaḥ 6.",
        noteType = NoteType.ColophonNote
      )
    )
  }

  it("ignores a colophon note which is leftover from the template") {
    // e.g. Greek/MS_354.xml
    val xml: Elem =
      <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="TeiNotes_Example_1">
        <teiHeader>
          <fileDesc>
            <sourceDesc>
              <msDesc xml:lang="en" xml:id="TeiNotes_Example_1">
                <msContents>
                  <colophon><!-- insert --> <locus></locus>
                    <!-- Use <note> to add general comment -->
                  </colophon>
                </msContents>
              </msDesc>
            </sourceDesc>
          </fileDesc>
        </teiHeader>
      </TEI>

    TeiNotes(xml) shouldBe empty
  }

  describe("implicit/explicit") {
    it("extracts the implicit/explicit notes") {
      // e.g. Indic/Indic_Alpha_932.xml
      val xml: Elem =
        <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="Wellcome_Alpha_932">
          <teiHeader xml:lang="eng">
            <fileDesc>
              <sourceDesc>
                <msDesc>
                  <msContents>
                    <incipit> <locus>F. 1v</locus>
                      <!-- transcript -->
                      oṃ namaḥ
                      japāpuṣyena saṃkāśaṃ kāśyapeyaṃ mahādyutiṃ
                      tam ahaṃ sarvapāpaghnaṃ praṇato smi divākaraṃ
                      sūryāya namaḥ
                    </incipit>

                    <explicit> <locus>F. 3r</locus>
                      <!-- transcript -->
                      ||12|| navagrahastotraṃ saṃpūraṇaṃ
                    </explicit>
                  </msContents>
                </msDesc>
              </sourceDesc>
            </fileDesc>
          </teiHeader>
        </TEI>

      TeiNotes(xml) shouldBe List(
        Note(
          contents =
            "F. 1v: oṃ namaḥ japāpuṣyena saṃkāśaṃ kāśyapeyaṃ mahādyutiṃ tam ahaṃ sarvapāpaghnaṃ praṇato smi divākaraṃ sūryāya namaḥ",
          noteType = NoteType.BeginsNote
        ),
        Note(
          contents = "F. 3r: ||12|| navagrahastotraṃ saṃpūraṇaṃ",
          noteType = NoteType.EndsNote
        )
      )
    }

    it("handles an explicit note without a <locus>") {
      // e.g. Egyptian/Egyptian_MS_4.xml
      val xml: Elem =
        <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="Wellcome_Alpha_932">
          <teiHeader xml:lang="eng">
            <fileDesc>
              <sourceDesc>
                <msDesc>
                  <msContents>
                    <explicit> ink Mnw m prt. f
                      <note> the last legible line is the 10th line from the top and the penulitmate line of the column. This corresponds to <ref> Lepsius</ref> 17, line 11 </note>
                    </explicit>
                  </msContents>
                </msDesc>
              </sourceDesc>
            </fileDesc>
          </teiHeader>
        </TEI>

      TeiNotes(xml) shouldBe List(
        Note(
          contents =
            "ink Mnw m prt. f the last legible line is the 10th line from the top and the penulitmate line of the column. This corresponds to Lepsius 17, line 11",
          noteType = NoteType.EndsNote
        )
      )
    }
  }

  it("adds hand notes") {
    val id = "id"
    val result = TeiNotes(
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
    )

    result shouldBe List(
      Note(NoteType.HandNote, "neatly written text"),
      Note(NoteType.HandNote, "even more neatly written text")
    )
  }

  it("adds provenance") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(provenance("Once owned by Pwyll Pen Annwn"))
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote, "Once owned by Pwyll Pen Annwn")
    )
  }

  it("ignores empty provenance") {
    // Empty provenance elements should not exist, but the TEI files are manually
    // created from templates which contain a bunch of empty elements to be completed
    // by the author.  Sometimes they are left in.
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(provenance(""))
          )
        )
      )
    )
    result shouldBe List()
  }

  it("ignores empty provenance date bounds") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(
              provenance(
                "Once owned by Pwyll Pen Annwn",
                when = Some(""),
                notAfter = Some("")
              )
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote, "Once owned by Pwyll Pen Annwn")
    )
  }

  it("includes date bounds in provenance") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(
              provenance(
                "Owned by Frithuswith",
                notBefore = Some("701"),
                notAfter = Some("727-10-19")
              ),
              provenance(
                "Owned by Edith Forne",
                from = Some("1094-01-01"),
                notAfter = Some("1120")
              ),
              provenance(
                "Owned by Empress Matilda",
                from = Some("1141-04-08"),
                to = Some("1148-01-01")
              ),
              provenance(
                "Owned by Rosamund Clifford",
                notBefore = Some("1170"),
                to = Some("1176-12-25")
              )
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(
        NoteType.OwnershipNote,
        "(not before 701, not after 727-10-19): Owned by Frithuswith"
      ),
      Note(
        NoteType.OwnershipNote,
        "(from 1094-01-01, not after 1120): Owned by Edith Forne"
      ),
      Note(
        NoteType.OwnershipNote,
        "(from 1141-04-08, to 1148-01-01): Owned by Empress Matilda"
      ),
      Note(
        NoteType.OwnershipNote,
        "(not before 1170, to 1176-12-25): Owned by Rosamund Clifford"
      )
    )
  }

  it("includes provenance with a specific date") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance =
              List(provenance("Owned by Empress Matilda", when = Some("1145")))
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote, "(1145): Owned by Empress Matilda")
    )
  }

  it("includes provenance with one bound") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(
              provenance(
                "In the library of The Academy of Gondishapur",
                from = Some("452-12-25")
              ),
              provenance("Owned by Frithuswith", notBefore = Some("670")),
              provenance("Owned by Charles Pratt", notAfter = Some("1788")),
              provenance(
                "In the Charing Cross Library",
                to = Some("1998-05-01")
              )
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(
        NoteType.OwnershipNote,
        "(from 452-12-25): In the library of The Academy of Gondishapur"
      ),
      Note(NoteType.OwnershipNote, "(not before 670): Owned by Frithuswith"),
      Note(NoteType.OwnershipNote, "(not after 1788): Owned by Charles Pratt"),
      Note(
        NoteType.OwnershipNote,
        "(to 1998-05-01): In the Charing Cross Library"
      )
    )
  }

  it("includes all date bounds in a provenance note") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(
              provenance(
                "Owned by Herb Wells",
                from = Some("1901-12-25"),
                to = Some("1930-01-01"),
                notBefore = Some("1876"),
                notAfter = Some("1745"),
                when = Some("2022-07-29")
              )
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(
        NoteType.OwnershipNote,
        "(2022-07-29, from 1901-12-25, not before 1876, to 1930-01-01, not after 1745): Owned by Herb Wells"
      )
    )
  }

  it("includes provenance and acquisition together") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace = None,
            originDates = Nil,
            provenance = List(
              provenance(
                "Owned by Charles Pratt",
                notAfter = Some("1794-04-18")
              )
            ),
            acquisition = List(
              acquisition(
                "Gifted to HSW by Silas Burroughs",
                when = Some("1892-08-21")
              )
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(
        NoteType.OwnershipNote,
        "(not after 1794-04-18): Owned by Charles Pratt"
      ),
      Note(
        NoteType.AcquisitionNote,
        "(1892-08-21): Gifted to HSW by Silas Burroughs"
      )
    )
  }

  it("ignores the handNote if it has a scribe attribute") {
    val id = ""
    val result = TeiNotes(
      teiXml(
        id,
        physDesc = Some(
          physDesc(handNotes =
            List(handNotes(label = "Wanda Maximoff", scribe = Some("sole")))
          )
        )
      )
    )

    result shouldBe Nil
  }

  it("includes the persname in the note if there is a label") {
    val id = ""
    val result = TeiNotes(
      teiXml(
        id,
        physDesc = Some(
          physDesc(handNotes =
            List(
              handNotes(
                label = "In neat handwriting by ",
                persNames = List(
                  scribe("Tony Stark", `type` = Some("original"))
                )
              )
            )
          )
        )
      )
    )

    result shouldBe List(
      Note(NoteType.HandNote, "In neat handwriting by Tony Stark")
    )
  }

  it("does not include the persName in the note if there is not a label") {
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        physDesc = Some(
          physDesc(handNotes =
            List(
              handNotes(
                persNames = List(
                  scribe("Tony Stark", `type` = Some("original"))
                )
              )
            )
          )
        )
      )
    )

    result shouldBe Nil
  }

  describe("hand notes") {
    it("keeps the text of a handNote with scribe=\"multiple\"") {
      TeiNotes(
        teiXml(
          physDesc = Some(
            physDesc(handNotes =
              List(
                handNotes(
                  label = "Written by several hands",
                  scribe = Some("multiple")
                ),
                handNotes(label = "Two hands", scribe = Some("Multiple")),
                handNotes(label = "Many hands", scribe = Some("various"))
              )
            )
          )
        )
      ) shouldBe List(
        Note(NoteType.HandNote, "Written by several hands"),
        Note(NoteType.HandNote, "Two hands"),
        Note(NoteType.HandNote, "Many hands")
      )
    }

    it("adds the script to the hand note") {
      TeiNotes(
        teiXml(
          physDesc = Some(
            physDesc(handNotes =
              List(
                <handNote script="testscript">A small, neat hand</handNote>,
                <handNote script="otherscript"/>,
                <handNote script="test_script"/>,
                <handNote script="testSquare"/>,
                <handNote script="&lt;!-- Insert script name --&gt;"/>
              )
            )
          )
        )
      ) shouldBe List(
        Note(NoteType.HandNote, "Script: Testscript<br/>A small, neat hand"),
        Note(NoteType.HandNote, "Script: Otherscript"),
        Note(NoteType.HandNote, "Script: Testscript"),
        Note(NoteType.HandNote, "Script: Test square")
      )
    }

    it("does not repeat a script the hand note already names") {
      TeiNotes(
        teiXml(
          physDesc = Some(
            physDesc(handNotes =
              List(
                <handNote script="testscript">Script: neat Testscript</handNote>,
                <handNote script="test">Testing hand</handNote>,
                <handNote script="te_st"><p><label>Script:</label> teʿśt</p></handNote>,
                <handNote script="other">Square script</handNote>,
                <handNote script="testSquare">Neat test square</handNote>,
                <handNote script="test">The script is small and neat</handNote>,
                <handNote script="other"><p><label>Ink:</label> Black</p><seg type="density">300</seg><seg type="ratio">1:1</seg></handNote>,
                <handNote script=""/>
              )
            )
          )
        )
      ) shouldBe List(
        Note(NoteType.HandNote, "Script: neat Testscript"),
        Note(NoteType.HandNote, "Script: Test<br/>Testing hand"),
        Note(NoteType.HandNote, "Script: teʿśt"),
        Note(NoteType.HandNote, "Script: Other<br/>Square script"),
        Note(NoteType.HandNote, "Neat test square"),
        Note(
          NoteType.HandNote,
          "Script: Test<br/>The script is small and neat"
        ),
        Note(NoteType.HandNote, "Script: Other<br/>Ink: Black")
      )
    }
  }

  describe("physical description notes") {
    val fullPhysDesc =
      <physDesc>
        <objectDesc>
          <supportDesc>
            <support>Paper</support>
            <foliation>Foliated in pencil.</foliation>
            <collation><formula>1-4 (8)</formula></collation>
            <condition>Some
              water damage.</condition>
          </supportDesc>
          <layoutDesc>
            <layout columns="2" writtenLines="20">20</layout>
            <layout columns="1">
              <dimensions type="line-height" unit="mm"><height>9</height></dimensions>
            </layout>
            <layout>Ruled in drypoint.</layout>
          </layoutDesc>
        </objectDesc>
        <decoDesc>
          <decoNote type="rubrication">Headings in red.</decoNote>
          <decoNote type="border">Border on the first leaf.</decoNote>
          <decoNote type="other">A test decoration.</decoNote>
        </decoDesc>
        <bindingDesc>
          <binding><p>Leather over boards.</p></binding>
        </bindingDesc>
      </physDesc>

    it(
      "makes notes for layout, foliation, collation, decoration, binding and condition, in that order"
    ) {
      TeiNotes(teiXml(physDesc = Some(fullPhysDesc))) shouldBe List(
        Note(NoteType.LayoutNote, "2 columns; 20 written lines"),
        Note(NoteType.LayoutNote, "1 column"),
        Note(NoteType.LayoutNote, "Ruled in drypoint."),
        Note(NoteType.FoliationNote, "Foliated in pencil."),
        Note(NoteType.CollationNote, "1-4 (8)"),
        Note(NoteType.DecorationNote, "Rubrication: Headings in red."),
        Note(NoteType.DecorationNote, "Border on the first leaf."),
        Note(NoteType.DecorationNote, "A test decoration."),
        Note(NoteType.BindingInformation, "Leather over boards."),
        Note(NoteType.ConditionNote, "Some water damage.")
      )
    }

    it("makes the notes for an msPart") {
      val part = msPart(id = "part_1", physDesc = Some(fullPhysDesc))
      TeiNotes(List(part)).map(_.noteType) should contain allOf (
        NoteType.BindingInformation,
        NoteType.DecorationNote,
        NoteType.ConditionNote,
        NoteType.LayoutNote,
        NoteType.CollationNote,
        NoteType.FoliationNote
      )
    }

    it("describes the layout from its attributes, locus and text") {
      val layoutPhysDesc =
        <physDesc>
          <objectDesc>
            <layoutDesc>
              <layout writtenLines="30">30 written lines</layout>
              <layout columns="1" ruledLines="12">
                <locus>1a-9b</locus>
                <dimensions type="line-height" unit="mm"><height>8</height></dimensions>
              </layout>
              <layout writtenLines="18"><note>Ruled in red.</note><measure unit="mm">90</measure></layout>
              <layout columns="1 2" writtenLines="20 24"/>
              <layout writtenLines="16 18">16-18 lines to a page</layout>
              <layout columns="1" writtenLines="18" ruledLines="18"> 1 column, 18 lines. </layout>
              <layout writtenLines="20">Ruling changes on folio 20.</layout>
              <layout writtenLines="20" ruledLines="20">20 ruled lines</layout>
              <layout writtenLines="20 24">20 lines on the first leaf.</layout>
              <layout ruledLines="6"><locus>f. 2r:</locus> 6 lines.</layout>
              <layout ruledLines="6"><locus>f. 3r: </locus> 6 lines.</layout>
              <layout>(drypoint)<locus>(f. 3)</locus>, ink <locus>(f. 4)</locus>.</layout>
              <layout writtenLines="&lt;!-- Insert number here --&gt;"/>
            </layoutDesc>
          </objectDesc>
        </physDesc>

      TeiNotes(teiXml(physDesc = Some(layoutPhysDesc))) shouldBe List(
        Note(NoteType.LayoutNote, "30 written lines"),
        Note(NoteType.LayoutNote, "1a-9b: 1 column; 12 ruled lines"),
        Note(NoteType.LayoutNote, "18 written lines; Ruled in red."),
        Note(NoteType.LayoutNote, "1 to 2 columns; 20 to 24 written lines"),
        Note(NoteType.LayoutNote, "16-18 lines to a page"),
        Note(NoteType.LayoutNote, "1 column, 18 lines."),
        Note(
          NoteType.LayoutNote,
          "20 written lines; Ruling changes on folio 20."
        ),
        Note(NoteType.LayoutNote, "20 written lines; 20 ruled lines"),
        Note(
          NoteType.LayoutNote,
          "20 to 24 written lines; 20 lines on the first leaf."
        ),
        Note(NoteType.LayoutNote, "f. 2r: 6 lines."),
        Note(NoteType.LayoutNote, "f. 3r: 6 lines."),
        Note(NoteType.LayoutNote, "(drypoint) (f. 3), ink (f. 4).")
      )
    }

    it("reads layoutDesc paragraphs") {
      val layoutPhysDesc =
        <physDesc>
          <objectDesc>
            <layoutDesc><p>Written in two columns.</p></layoutDesc>
          </objectDesc>
        </physDesc>

      TeiNotes(teiXml(physDesc = Some(layoutPhysDesc))) shouldBe List(
        Note(NoteType.LayoutNote, "Written in two columns.")
      )
    }

    it("separates phrases in nested elements and keeps measures with words") {
      val nestedPhysDesc =
        <physDesc>
          <decoDesc>
            <decoNote type="illustration"><locus>f. 1<hi>r</hi></locus>Test drawing</decoNote>
            <decoNote type="border"><locus>f. 2r</locus><!-- insert description --><measure>90</measure>Test frame</decoNote>
            <decoNote type="unwan">ʿUnwān in red</decoNote>
            <decoNote type="rubrication">Occasional rubrication</decoNote>
          </decoDesc>
          <bindingDesc>
            <binding><p>Leather</p><p>Rebacked</p></binding>
            <binding><p>unbound<note>Test folder</note></p></binding>
            <binding><p>Chain distance ca. <measure>26 mm</measure>, <measure>90</measure>no watermark</p></binding>
          </bindingDesc>
        </physDesc>

      TeiNotes(teiXml(physDesc = Some(nestedPhysDesc))) shouldBe List(
        Note(NoteType.DecorationNote, "Illustration: f. 1r Test drawing"),
        Note(NoteType.DecorationNote, "Border: f. 2r Test frame"),
        Note(NoteType.DecorationNote, "ʿUnwān in red"),
        Note(NoteType.DecorationNote, "Occasional rubrication"),
        Note(NoteType.BindingInformation, "Leather Rebacked"),
        Note(NoteType.BindingInformation, "unbound Test folder"),
        Note(
          NoteType.BindingInformation,
          "Chain distance ca. 26 mm, no watermark"
        )
      )
    }

    it("makes a note for each binding, and reads decoDesc paragraphs") {
      val physDescWithParagraphs =
        <physDesc>
          <decoDesc>
            <p>Headings in red.</p>
          </decoDesc>
          <bindingDesc>
            <binding><p>Original leather.</p></binding><binding><p><seg><dimensions unit="mm"><height>100</height></dimensions>Rebacked.</seg></p></binding>
          </bindingDesc>
          <bindingDesc>
            <p>Limp vellum.</p>
          </bindingDesc>
        </physDesc>

      TeiNotes(teiXml(physDesc = Some(physDescWithParagraphs))) shouldBe List(
        Note(NoteType.DecorationNote, "Headings in red."),
        Note(NoteType.BindingInformation, "Original leather."),
        Note(NoteType.BindingInformation, "Rebacked."),
        Note(NoteType.BindingInformation, "Limp vellum.")
      )
    }

    it("ignores elements left empty from the template") {
      val emptyPhysDesc =
        <physDesc>
          <objectDesc>
            <supportDesc>
              <foliation><!-- delete if not required --></foliation>
              <collation><formula/><signatures><!-- insert --></signatures></collation>
              <condition/>
            </supportDesc>
            <layoutDesc>
              <layout writtenLines=""><!-- delete if not required --></layout>
            </layoutDesc>
          </objectDesc>
          <decoDesc>
            <decoNote type="illustration"/>
            <decoNote type="marginal"><!-- delete if not required --></decoNote>
          </decoDesc>
          <bindingDesc>
            <binding><p><!-- Insert binding information here --></p></binding>
          </bindingDesc>
        </physDesc>

      TeiNotes(teiXml(physDesc = Some(emptyPhysDesc))) shouldBe Nil
    }
  }

  it("makes a filiation note for an msItem") {
    val item =
      <msItem xml:id="item_1">
        <filiation>Also copied in <ref target="test_ref">another manuscript</ref>.</filiation>
      </msItem>

    TeiNotes(List(item)) shouldBe List(
      Note(NoteType.RelatedMaterial, "Also copied in another manuscript.")
    )
  }
}
