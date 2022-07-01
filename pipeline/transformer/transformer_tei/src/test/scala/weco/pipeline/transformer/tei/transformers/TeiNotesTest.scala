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

  it("adds hand notes"){
    val id = "id"
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

  it("adds provenance"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(provenance("Once owned by Pwyll Pen Annwn"))
          )
        )
      )
    )
    result shouldBe List(Note(NoteType.OwnershipNote,"Once owned by Pwyll Pen Annwn"))
  }

  it("ignores empty provenance"){
    // Empty provenance elements should not exist, but the TEI files are manually
    // created from templates which contain a bunch of empty elements to be completed
    // by the author.  Sometimes they are left in.
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(provenance(""))
          )
        )
      )
    )
    result shouldBe List()
  }

  it("ignores empty provenance date bounds"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(provenance("Once owned by Pwyll Pen Annwn", when=Some(""), notAfter=Some("")))
          )
        )
      )
    )
    result shouldBe List(Note(NoteType.OwnershipNote,"Once owned by Pwyll Pen Annwn"))
  }

  it("includes date bounds in provenance"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(
              provenance("Owned by Frithuswith", notBefore = Some("701"), notAfter=Some("727-10-19")),
              provenance("Owned by Edith Forne", from = Some("1094-01-01"), notAfter=Some("1120")),
              provenance("Owned by Empress Matilda", from = Some("1141-04-08"), to=Some("1148-01-01")),
              provenance("Owned by Rosamund Clifford", notBefore = Some("1170"), to=Some("1176-12-25"))
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote,"(not before 701, not after 727-10-19): Owned by Frithuswith"),
      Note(NoteType.OwnershipNote,"(from 1094-01-01, not after 1120): Owned by Edith Forne"),
      Note(NoteType.OwnershipNote,"(from 1141-04-08, to 1148-01-01): Owned by Empress Matilda"),
      Note(NoteType.OwnershipNote,"(not before 1170, to 1176-12-25): Owned by Rosamund Clifford")
    )
  }

  it("includes provenance with a specific date"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(provenance("Owned by Empress Matilda", when = Some("1145")))
          )
        )
      )
    )
    result shouldBe List(Note(NoteType.OwnershipNote,"(1145): Owned by Empress Matilda"))
  }

  it("includes provenance with one bound"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(
              provenance("In the library of The Academy of Gondishapur", from = Some("452-12-25")),
              provenance("Owned by Frithuswith", notBefore = Some("670")),
              provenance("Owned by Charles Pratt", notAfter = Some("1788")),
              provenance("In the Charing Cross Library", to = Some("1998-05-01"))
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote,"(from 452-12-25): In the library of The Academy of Gondishapur"),
      Note(NoteType.OwnershipNote,"(not before 670): Owned by Frithuswith"),
      Note(NoteType.OwnershipNote,"(not after 1788): Owned by Charles Pratt"),
      Note(NoteType.OwnershipNote,"(to 1998-05-01): In the Charing Cross Library")
    )
  }

  it("includes all date bounds in a provenance note"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(
              provenance(
                "Owned by Herb Wells",
                from = Some("1901-12-25"),
                to=Some("1930-01-01"),
                notBefore = Some("1876"),
                notAfter = Some("1745"),
                when = Some("2022-07-29")),
            )
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote,"(2022-07-29, from 1901-12-25, not before 1876, to 1930-01-01, not after 1745): Owned by Herb Wells"),
    )
  }

  it("includes provenance and acquisition together"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        history = Some(
          history(
            origPlace=None,
            originDates = Nil,
            provenance = List(provenance("Owned by Charles Pratt", notAfter = Some("1794-04-18"))),
            acquisition = List(acquisition("Gifted to HSW by Silas Burroughs", when = Some("1892-08-21")))
          )
        )
      )
    )
    result shouldBe List(
      Note(NoteType.OwnershipNote,"(not after 1794-04-18): Owned by Charles Pratt"),
      Note(NoteType.AcquisitionNote,"(1892-08-21): Gifted to HSW by Silas Burroughs"),
    )
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

  it("includes the persname in the note if there is a label"){
    val id = ""
    val result = TeiNotes(
      teiXml(
        id,
        physDesc= Some(physDesc(handNotes = List(
          handNotes(
            label = "In neat handwriting by ",
            persNames = List(
              scribe("Tony Stark", `type` = Some("original")),
              )))))
        )
      )


    result shouldBe List(Note(NoteType.HandNote, "In neat handwriting by Tony Stark"))
  }

  it("does not include the persName in the note if there is not a label"){
    val id = "id"
    val result = TeiNotes(
      teiXml(
        id,
        physDesc= Some(physDesc(handNotes = List(
          handNotes(
            persNames = List(
              scribe("Tony Stark", `type` = Some("original")),
              )))))
        )
      )

    result shouldBe Nil
  }
}
