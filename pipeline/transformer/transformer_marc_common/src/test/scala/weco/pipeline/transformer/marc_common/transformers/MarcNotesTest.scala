package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcNotesTest extends AnyFunSpec with Matchers {
  private def recordWithNotes(notes: Seq[(String, Note)]): MarcTestRecord =
    MarcTestRecord(fields = notes.map {
      case (tag, note) =>
        MarcField(
          marcTag = tag,
          subfields = Seq(MarcSubfield(tag = "a", content = note.contents))
        )
    })

  it("extracts notes from all fields") {
    val notes = List(
      "500" -> Note(
        contents = "general note a",
        noteType = NoteType.GeneralNote
      ),
      "501" -> Note(
        contents = "general note b",
        noteType = NoteType.GeneralNote
      ),
      "502" -> Note(
        contents = "dissertation note",
        noteType = NoteType.DissertationNote
      ),
      "504" -> Note(
        contents = "bib info a",
        noteType = NoteType.BibliographicalInformation
      ),
      "505" -> Note(
        contents = "contents note",
        noteType = NoteType.ContentsNote
      ),
      "506" -> Note(
        contents = "typical terms of use",
        noteType = NoteType.TermsOfUse
      ),
      "508" -> Note(
        contents = "credits note a",
        noteType = NoteType.CreditsNote
      ),
      "510" -> Note(
        contents = "references a",
        noteType = NoteType.ReferencesNote
      ),
      "511" -> Note(
        contents = "credits note b",
        noteType = NoteType.CreditsNote
      ),
      "514" -> Note(
        contents = "Completeness:",
        noteType = NoteType.LetteringNote
      ),
      "518" -> Note(
        contents = "time and place note",
        noteType = NoteType.TimeAndPlaceNote
      ),
      "524" -> Note(contents = "cite as note", noteType = NoteType.CiteAsNote),
      "533" -> Note(
        contents = "reproduction a",
        noteType = NoteType.ReproductionNote
      ),
      "534" -> Note(
        contents = "reproduction b",
        noteType = NoteType.ReproductionNote
      ),
      "535" -> Note(
        contents = "location of original note",
        noteType = NoteType.LocationOfOriginalNote
      ),
      "536" -> Note(
        contents = "funding information",
        noteType = NoteType.FundingInformation
      ),
      "540" -> Note(contents = "terms of use", noteType = NoteType.TermsOfUse),
      "542" -> Note(
        contents = "copyright a",
        noteType = NoteType.CopyrightNote
      ),
      "544" -> Note(
        contents = "related material a",
        noteType = NoteType.RelatedMaterial
      ),
      "545" -> Note(
        contents = "bib info b",
        noteType = NoteType.BiographicalNote
      ),
      "546" -> Note(
        contents = "Marriage certificate: German; Fraktur.",
        noteType = NoteType.LanguageNote
      ),
      "547" -> Note(
        contents = "general note c",
        noteType = NoteType.GeneralNote
      ),
      "562" -> Note(
        contents = "general note d",
        noteType = NoteType.GeneralNote
      ),
      "563" -> Note(
        contents = "binding info note",
        noteType = NoteType.BindingInformation
      ),
      "581" -> Note(
        contents = "publications b",
        noteType = NoteType.PublicationsNote
      ),
      "585" -> Note(
        contents = "exhibitions",
        noteType = NoteType.ExhibitionsNote
      ),
      "586" -> Note(contents = "awards", noteType = NoteType.AwardsNote),
      "591" -> Note(
        contents = "A general, unspecified note",
        noteType = NoteType.GeneralNote
      ),
      "593" -> Note(contents = "copyright b", noteType = NoteType.CopyrightNote)
    )
    MarcNotes(recordWithNotes(notes)) shouldBe notes.map(_._2)
  }

  it("extracts all notes when duplicate fields") {
    val notes = List(
      "500" -> Note(contents = "note a", noteType = NoteType.GeneralNote),
      "500" -> Note(contents = "note b", noteType = NoteType.GeneralNote)
    )
    MarcNotes(recordWithNotes(notes)) shouldBe notes.map(_._2)
  }

  it("does not extract notes from non-note fields") {
    val fields = List(
      "100" -> "not a note",
      "530" -> "not a note"
    )
    MarcNotes(MarcTestRecord(fields = fields map {
      case (tag, content) =>
        MarcField(
          marcTag = tag,
          subfields = Seq(MarcSubfield(tag = "a", content = content))
        )
    })) shouldBe Nil
  }

  it("preserves HTML in notes fields") {
    val notes = List(
      "500" -> Note(contents = "<p>note</p>", noteType = NoteType.GeneralNote)
    )
    MarcNotes(recordWithNotes(notes)) shouldBe notes.map(_._2)
  }

  it(
    "concatenate all the subfields on a single MARC field into a single note"
  ) {
    val recordWithNotes = MarcTestRecord(
      fields = List(
        MarcField(
          marcTag = "500",
          subfields = List(
            MarcSubfield(tag = "a", content = "1st part."),
            MarcSubfield(tag = "b", content = "2nd part."),
            MarcSubfield(tag = "c", content = "3rd part.")
          )
        )
      )
    )
    MarcNotes(recordWithNotes) shouldBe List(
      Note(
        contents = "1st part. 2nd part. 3rd part.",
        noteType = NoteType.GeneralNote
      )
    )
  }

  it("does not concatenate separate varfields") {
    val notes = List(
      "500" -> Note(contents = "1st note.", noteType = NoteType.GeneralNote),
      "500" -> Note(contents = "2nd note.", noteType = NoteType.GeneralNote)
    )
    MarcNotes(recordWithNotes(notes)) shouldBe notes.map(_._2)
  }

  it("distinguishes based on the first indicator of 535") {
    val recordWithNotes = MarcTestRecord(
      fields = List(
        MarcField(
          marcTag = "535",
          indicator1 = "1",
          subfields =
            List(MarcSubfield(tag = "a", content = "The originals are in Oman"))
        ),
        MarcField(
          marcTag = "535",
          indicator1 = "2",
          subfields = List(
            MarcSubfield(tag = "a", content = "The duplicates are in Denmark")
          )
        )
      )
    )

    MarcNotes(recordWithNotes) shouldBe List(
      Note(
        contents = "The originals are in Oman",
        noteType = NoteType.LocationOfOriginalNote
      ),
      Note(
        contents = "The duplicates are in Denmark",
        noteType = NoteType.LocationOfDuplicatesNote
      )
    )
  }

  it("only gets an ownership note if 561 1st indicator is 1") {
    val recordWithNotes = MarcTestRecord(
      fields = List(
        MarcField(
          marcTag = "561",
          indicator1 = "1",
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Provenance: one plate in the set of plates"
            )
          )
        ),
        MarcField(
          marcTag = "561",
          indicator1 = "0",
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Purchased from John Smith on 01/01/2001"
            )
          )
        ),
        MarcField(
          marcTag = "561",
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Private contact details for John Smith"
            )
          )
        )
      )
    )
    MarcNotes(recordWithNotes) shouldBe List(
      Note(
        contents = "Provenance: one plate in the set of plates",
        noteType = NoteType.OwnershipNote
      )
    )
  }

  it("suppresses MarcSubfield ǂ5 universally") {
    val notesFields = MarcNotes.notesFields.keys.map(
      key => {
        MarcField(
          marcTag = key,
          subfields = List(
            MarcSubfield(tag = "a", content = "Main bit."),
            MarcSubfield(tag = "5", content = "UkLW")
          )
        )
      }
    )
    val recordWithNotes = MarcTestRecord(
      fields = notesFields.toList
    )

    val notes = MarcNotes.notesFields.values
      .map(
        createNote =>
          createNote(
            MarcField(
              marcTag = "000",
              subfields = List(
                MarcSubfield(tag = "a", content = "Main bit.")
              )
            )
          )
      )
      .toList

    MarcNotes(recordWithNotes) should contain theSameElementsAs notes
  }

  it("skips 591 MarcSubfield ǂ9 (barcode)") {
    val fields = List(
      MarcField(
        marcTag = "591",
        subfields = List(
          MarcSubfield(tag = "z", content = "Copy 1."),
          MarcSubfield(
            tag = "e",
            content =
              "Note: The author's presentation inscription on verso of 2nd leaf."
          ),
          MarcSubfield(tag = "9", content = "X8253")
        )
      )
    )

    val recordWithNotes = MarcTestRecord(fields = fields)

    MarcNotes(recordWithNotes) should contain theSameElementsAs List(
      Note(
        contents =
          "Copy 1. Note: The author's presentation inscription on verso of 2nd leaf.",
        noteType = NoteType.GeneralNote
      )
    )
  }

  it("skips notes which are just whitespace") {
    val fields =
      List("\u00a0", "", "\t\n").map {
        content =>
          MarcField(
            marcTag = "535",
            subfields = List(
              MarcSubfield(tag = "a", content = content)
            )
          )
      }

    val recordWithNotes = MarcTestRecord(fields)

    MarcNotes(recordWithNotes) shouldBe empty
  }

  it("creates a clickable link for MarcSubfield ǂu") {
    // This example is taken from b30173140
    val fields = List(
      MarcField(
        marcTag = "540",
        subfields = List(
          MarcSubfield(
            tag = "a",
            content =
              "The National Library of Medicine believes this item to be in the public domain."
          ),
          MarcSubfield(
            tag = "u",
            content = "https://creativecommons.org/publicdomain/mark/1.0/"
          ),
          MarcSubfield(tag = "5", content = "DNLM")
        )
      )
    )

    val recordWithNotes = MarcTestRecord(fields = fields)

    MarcNotes(recordWithNotes).map(_.contents) shouldBe List(
      "The National Library of Medicine believes this item to be in the public domain. <a href=\"https://creativecommons.org/publicdomain/mark/1.0/\">https://creativecommons.org/publicdomain/mark/1.0/</a>"
    )
  }

  it(
    "doesn't create a clickable link if MarcSubfield ǂu doesn't look like a URL"
  ) {
    val fields = List(
      MarcField(
        marcTag = "540",
        subfields = List(
          MarcSubfield(
            tag = "a",
            content =
              "The National Library of Medicine believes this item to be in the public domain."
          ),
          MarcSubfield(tag = "u", content = "CC-0 license")
        )
      )
    )

    val recordWithNotes = MarcTestRecord(fields)

    MarcNotes(recordWithNotes).map(_.contents) shouldBe List(
      "The National Library of Medicine believes this item to be in the public domain. CC-0 license"
    )
  }

  it("strips whitespace from the MarcSubfield ǂu") {
    // This example is based on b33032440
    val fields = List(
      MarcField(
        marcTag = "540",
        subfields = List(
          MarcSubfield(
            tag = "u",
            content = "https://wellcomecollection.org/works/a65fex5m "
          )
        )
      )
    )

    val recordWithNotes = MarcTestRecord(fields)

    MarcNotes(recordWithNotes).map(_.contents) shouldBe List(
      "<a href=\"https://wellcomecollection.org/works/a65fex5m\">https://wellcomecollection.org/works/a65fex5m</a>"
    )
  }

  describe("related material in MARC field 787") {
    it("gets a related material note from 787") {
      // This example is based on b33032440
      val fields = List(
        MarcField(
          marcTag = "787",
          subfields = List(
            MarcSubfield(tag = "t", content = "Daily telegraph."),
            MarcSubfield(tag = "g", content = "1989")
          )
        )
      )

      val recordWithNotes =
        MarcTestRecord(fields)

      MarcNotes(recordWithNotes) shouldBe List(
        Note(
          contents = "Daily telegraph. 1989",
          noteType = NoteType.RelatedMaterial
        )
      )
    }

    it("creates a search link for b-numbers in ǂw") {
      // This example is based on b33039136
      val fields = List(
        MarcField(
          marcTag = "787",
          subfields = List(
            MarcSubfield(tag = "i", content = "Complemented by (work):"),
            MarcSubfield(tag = "t", content = "Depression ain't the sads."),
            MarcSubfield(tag = "w", content = "(UkLW)b33039112")
          )
        )
      )

      val recordWithNotes =
        MarcTestRecord(fields)

      MarcNotes(recordWithNotes) shouldBe List(
        Note(
          contents =
            "Complemented by (work): Depression ain't the sads. (<a href=\"https://wellcomecollection.org/search/works?query=b33039112\">b33039112</a>)",
          noteType = NoteType.RelatedMaterial
        )
      )
    }

    it("doesn't create a search link if ǂw isn't a b number") {
      // This example is based on b15900976
      val fields = List(
        MarcField(
          marcTag = "787",
          subfields = List(
            MarcSubfield(
              tag = "s",
              content = "Times (London, England :  1788)."
            ),
            MarcSubfield(tag = "g", content = "May 27, 2004."),
            MarcSubfield(tag = "w", content = "(OCoLC)6967919")
          )
        )
      )

      val recordWithNotes =
        MarcTestRecord(fields)

      MarcNotes(recordWithNotes) shouldBe List(
        Note(
          contents =
            "Times (London, England :  1788). May 27, 2004. (OCoLC)6967919",
          noteType = NoteType.RelatedMaterial
        )
      )
    }
  }

  describe("lettering note") {
    it("doesn't create a note from 246 .6 ǂa or 514 for visual material") {
      // This is based on b16529888
      val recordWithNotes = MarcTestRecord(
        materialTypeId = Some("k"),
        fields = List(
          MarcField(
            marcTag = "514",
            subfields = List(
              MarcSubfield(
                tag = "a",
                content =
                  "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
              )
            )
          ),
          MarcField(
            marcTag = "246",
            indicator2 = "6",
            subfields = List(
              MarcSubfield(
                tag = "a",
                content = "Le m\u00e9decin et la garde malade. H.D. ..."
              )
            )
          )
        )
      )

      MarcNotes(recordWithNotes) shouldBe empty
    }

    it("uses 514 for non-visual material") {
      val recordWithNotes = MarcTestRecord(
        materialTypeId = Some("not-k"),
        fields = List(
          MarcField(
            marcTag = "514",
            subfields = List(
              MarcSubfield(
                tag = "a",
                content =
                  "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
              )
            )
          ),
          MarcField(
            marcTag = "246",
            indicator2 = "6",
            subfields = List(
              MarcSubfield(
                tag = "a",
                content = "Le m\u00e9decin et la garde malade. H.D. ..."
              )
            )
          )
        )
      )

      MarcNotes(recordWithNotes) shouldBe List(
        Note(
          noteType = NoteType.LetteringNote,
          contents =
            "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
        )
      )
    }
  }

}
