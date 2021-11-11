package weco.pipeline.transformer.tei.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Note, NoteType}

import scala.xml.Elem

class TeiNotesTest extends AnyFunSpec with Matchers {
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
      Note(contents = "f. 31v.21 وليكن هذا آخر الكلام والحمد لله على التمام", noteType = NoteType.ColophonNote)
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
}
