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
        noteType = NoteType.ColophonNote)
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
        noteType = NoteType.ColophonNote)
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
          noteType = NoteType.EndsNote),
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
        ),
      )
    }
  }

  it("adds hand(?) notes"){
    val id = ""
    val result = TeiNotes(
      teiXml(
        id,
        physDesc = Some(physDesc(handNotes = List(
          handNotes(
            label = "neatly written text"),handNotes(
            label = "even more neatly written text")))
        )
      )
    )

    result shouldBe List(Note(NoteType.HandNote, "neatly written text"), Note(NoteType.HandNote, "even more neatly written text"))
  }

  it("ignores the handNote if it has a scribe attribute"){
    val id = ""
    val result = TeiNotes(
      teiXml(
        id,
        physDesc = Some(physDesc(handNotes = List(
          handNotes(
            label = "Wanda Maximoff",
            scribe = Some("sole"))))
        )
      )
    )

    result shouldBe Nil
  }

  it("ignores persName with role=scr in the note"){
    val id = ""
    val result = TeiNotes(
      teiXml(
        id,
        physDesc= Some(physDesc(handNotes = List(
          handNotes(
            label = "In neat handwriting",
            persNames = List(
              scribe("Tony Stark", `type` = Some("original")),
              )))))
        )
      )


    result shouldBe List(Note(NoteType.HandNote, "In neat handwriting"))
  }
}
