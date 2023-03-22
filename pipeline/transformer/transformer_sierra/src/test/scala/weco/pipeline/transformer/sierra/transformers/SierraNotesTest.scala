package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.{Subfield, VarField}

class SierraNotesTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("extracts notes from all fields") {
    val notes = List(
      "500" -> Note(
        contents = "general note a",
        noteType = NoteType.GeneralNote),
      "501" -> Note(
        contents = "general note b",
        noteType = NoteType.GeneralNote),
      "502" -> Note(
        contents = "dissertation note",
        noteType = NoteType.DissertationNote),
      "504" -> Note(
        contents = "bib info a",
        noteType = NoteType.BibliographicalInformation),
      "505" -> Note(
        contents = "contents note",
        noteType = NoteType.ContentsNote),
      "506" -> Note(
        contents = "typical terms of use",
        noteType = NoteType.TermsOfUse),
      "508" -> Note(
        contents = "credits note a",
        noteType = NoteType.CreditsNote),
      "510" -> Note(
        contents = "references a",
        noteType = NoteType.ReferencesNote),
      "511" -> Note(
        contents = "credits note b",
        noteType = NoteType.CreditsNote),
      "514" -> Note(
        contents = "Completeness:",
        noteType = NoteType.LetteringNote),
      "518" -> Note(
        contents = "time and place note",
        noteType = NoteType.TimeAndPlaceNote),
      "524" -> Note(contents = "cite as note", noteType = NoteType.CiteAsNote),
      "533" -> Note(
        contents = "reproduction a",
        noteType = NoteType.ReproductionNote),
      "534" -> Note(
        contents = "reproduction b",
        noteType = NoteType.ReproductionNote),
      "535" -> Note(
        contents = "location of original note",
        noteType = NoteType.LocationOfOriginalNote),
      "536" -> Note(
        contents = "funding information",
        noteType = NoteType.FundingInformation),
      "540" -> Note(contents = "terms of use", noteType = NoteType.TermsOfUse),
      "542" -> Note(
        contents = "copyright a",
        noteType = NoteType.CopyrightNote),
      "544" -> Note(
        contents = "related material a",
        noteType = NoteType.RelatedMaterial),
      "545" -> Note(
        contents = "bib info b",
        noteType = NoteType.BiographicalNote),
      "546" -> Note(
        contents = "Marriage certificate: German; Fraktur.",
        noteType = NoteType.LanguageNote),
      "547" -> Note(
        contents = "general note c",
        noteType = NoteType.GeneralNote),
      "562" -> Note(
        contents = "general note d",
        noteType = NoteType.GeneralNote),
      "563" -> Note(
        contents = "binding info note",
        noteType = NoteType.BindingInformation),
      "581" -> Note(
        contents = "publications b",
        noteType = NoteType.PublicationsNote),
      "585" -> Note(
        contents = "exhibitions",
        noteType = NoteType.ExhibitionsNote),
      "586" -> Note(contents = "awards", noteType = NoteType.AwardsNote),
      "591" -> Note(
        contents = "A general, unspecified note",
        noteType = NoteType.GeneralNote),
      "593" -> Note(
        contents = "copyright b",
        noteType = NoteType.CopyrightNote),
    )
    SierraNotes(bibData(notes)) shouldBe notes.map(_._2)
  }

  it("extracts all notes when duplicate fields") {
    val notes = List(
      "500" -> Note(contents = "note a", noteType = NoteType.GeneralNote),
      "500" -> Note(contents = "note b", noteType = NoteType.GeneralNote),
    )
    SierraNotes(bibData(notes)) shouldBe notes.map(_._2)
  }

  it("does not extract notes from non-note fields") {
    val notes = List(
      "100" -> "not a note",
      "530" -> "not a note",
    )
    SierraNotes(bibData(notes: _*)) shouldBe Nil
  }

  it("preserves HTML in notes fields") {
    val notes = List(
      "500" -> Note(contents = "<p>note</p>", noteType = NoteType.GeneralNote))
    SierraNotes(bibData(notes)) shouldBe notes.map(_._2)
  }

  it("concatenate all the subfields on a single MARC field into a single note") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "500",
          subfields = List(
            Subfield(tag = "a", content = "1st part."),
            Subfield(tag = "b", content = "2nd part."),
            Subfield(tag = "c", content = "3rd part."),
          )
        )
      )
    )
    SierraNotes(bibData) shouldBe List(
      Note(
        contents = "1st part. 2nd part. 3rd part.",
        noteType = NoteType.GeneralNote)
    )
  }

  it("does not concatenate separate varfields") {
    val notes = List(
      "500" -> Note(contents = "1st note.", noteType = NoteType.GeneralNote),
      "500" -> Note(contents = "2nd note.", noteType = NoteType.GeneralNote),
    )
    SierraNotes(bibData(notes)) shouldBe notes.map(_._2)
  }

  it("distinguishes based on the first indicator of 535") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "535",
          indicator1 = Some("1"),
          subfields =
            List(Subfield(tag = "a", content = "The originals are in Oman"))
        ),
        createVarFieldWith(
          marcTag = "535",
          indicator1 = Some("2"),
          subfields =
            List(Subfield(tag = "a", content = "The duplicates are in Denmark"))
        )
      )
    )

    SierraNotes(bibData) shouldBe List(
      Note(
        contents = "The originals are in Oman",
        noteType = NoteType.LocationOfOriginalNote),
      Note(
        contents = "The duplicates are in Denmark",
        noteType = NoteType.LocationOfDuplicatesNote),
    )
  }

  it("only gets an ownership note if 561 1st indicator is 1") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("561"),
          indicator1 = Some("1"),
          subfields = List(
            Subfield(
              tag = "a",
              content = "Provenance: one plate in the set of plates"),
          )
        ),
        VarField(
          marcTag = Some("561"),
          indicator1 = Some("0"),
          subfields = List(
            Subfield(
              tag = "a",
              content = "Purchased from John Smith on 01/01/2001"),
          )
        ),
        VarField(
          marcTag = Some("561"),
          indicator1 = None,
          subfields = List(
            Subfield(
              tag = "a",
              content = "Private contact details for John Smith"),
          )
        )
      )
    )
    SierraNotes(bibData) shouldBe List(
      Note(
        contents = "Provenance: one plate in the set of plates",
        noteType = NoteType.OwnershipNote)
    )
  }

  it("suppresses subfield ǂ5 universally") {
    val varFields = SierraNotes.notesFields.keys.map(key => {
      VarField(
        marcTag = key,
        subfields = List(
          Subfield(tag = "a", content = "Main bit."),
          Subfield(tag = "5", content = "UkLW"),
        )
      )
    })
    val bibData = createSierraBibDataWith(
      varFields = varFields.toList
    )

    val notes = SierraNotes.notesFields.values
      .map(
        createNote =>
          createNote(
            VarField(
              marcTag = "000",
              subfields = List(
                Subfield(tag = "a", content = "Main bit.")
              )
            )
        ))
      .toList

    SierraNotes(bibData) should contain theSameElementsAs notes
  }

  it("skips 591 subfield ǂ9 (barcode)") {
    val varFields = List(
      VarField(
        marcTag = "591",
        subfields = List(
          Subfield(tag = "z", content = "Copy 1."),
          Subfield(
            tag = "e",
            content =
              "Note: The author's presentation inscription on verso of 2nd leaf."),
          Subfield(tag = "9", content = "X8253")
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraNotes(bibData) should contain theSameElementsAs List(
      Note(
        contents =
          "Copy 1. Note: The author's presentation inscription on verso of 2nd leaf.",
        noteType = NoteType.GeneralNote
      )
    )
  }

  it("skips notes which are just whitespace") {
    val varFields =
      List("\u00a0", "", "\t\n").map { content =>
        VarField(
          marcTag = "535",
          subfields = List(
            Subfield(tag = "a", content = content),
          )
        )
      }

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraNotes(bibData) shouldBe empty
  }

  it("creates a clickable link for subfield ǂu") {
    // This example is taken from b30173140
    val varFields = List(
      VarField(
        marcTag = "540",
        subfields = List(
          Subfield(
            tag = "a",
            content =
              "The National Library of Medicine believes this item to be in the public domain."),
          Subfield(
            tag = "u",
            content = "https://creativecommons.org/publicdomain/mark/1.0/"),
          Subfield(tag = "5", content = "DNLM")
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraNotes(bibData).map(_.contents) shouldBe List(
      "The National Library of Medicine believes this item to be in the public domain. <a href=\"https://creativecommons.org/publicdomain/mark/1.0/\">https://creativecommons.org/publicdomain/mark/1.0/</a>"
    )
  }

  it("doesn't create a clickable link if subfield ǂu doesn't look like a URL") {
    val varFields = List(
      VarField(
        marcTag = "540",
        subfields = List(
          Subfield(
            tag = "a",
            content =
              "The National Library of Medicine believes this item to be in the public domain."),
          Subfield(tag = "u", content = "CC-0 license"),
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraNotes(bibData).map(_.contents) shouldBe List(
      "The National Library of Medicine believes this item to be in the public domain. CC-0 license"
    )
  }

  it("strips whitespace from the subfield ǂu") {
    // This example is based on b33032440
    val varFields = List(
      VarField(
        marcTag = "540",
        subfields = List(
          Subfield(tag = "u", content = "https://wellcomecollection.org/works/a65fex5m "),
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    SierraNotes(bibData).map(_.contents) shouldBe List(
      "<a href=\"https://wellcomecollection.org/works/a65fex5m\">https://wellcomecollection.org/works/a65fex5m</a>"
    )
  }

  describe("related material in MARC field 787") {
    it("gets a related material note from 787") {
      // This example is based on b33032440
      val varFields = List(
        VarField(
          marcTag = "787",
          subfields = List(
            Subfield(tag = "t", content = "Daily telegraph."),
            Subfield(tag = "g", content = "1989"),
          )
        )
      )

      val bibData = createSierraBibDataWith(varFields = varFields)

      SierraNotes(bibData) shouldBe List(
        Note(
          contents = "Daily telegraph. 1989",
          noteType = NoteType.RelatedMaterial
        )
      )
    }

    it("creates a search link for b-numbers in ǂw") {
      // This example is based on b33039136
      val varFields = List(
        VarField(
          marcTag = "787",
          subfields = List(
            Subfield(tag = "i", content = "Complemented by (work):"),
            Subfield(tag = "t", content = "Depression ain't the sads."),
            Subfield(tag = "w", content = "(UkLW)b33039112"),
          )
        )
      )

      val bibData = createSierraBibDataWith(varFields = varFields)

      SierraNotes(bibData) shouldBe List(
        Note(
          contents = "Complemented by (work): Depression ain't the sads. (<a href=\"https://wellcomecollection.org/search/works?query=b33039112\">b33039112</a>)",
          noteType = NoteType.RelatedMaterial
        )
      )
    }

    it("doesn't create a search link if ǂw isn't a b number") {
      // This example is based on b15900976
      val varFields = List(
        VarField(
          marcTag = "787",
          subfields = List(
            Subfield(tag = "s", content = "Times (London, England :  1788)."),
            Subfield(tag = "g", content = "May 27, 2004."),
            Subfield(tag = "w", content = "(OCoLC)6967919"),
          )
        )
      )

      val bibData = createSierraBibDataWith(varFields = varFields)

      SierraNotes(bibData) shouldBe List(
        Note(
          contents = "Times (London, England :  1788). May 27, 2004. (OCoLC)6967919",
          noteType = NoteType.RelatedMaterial
        )
      )
    }
  }

  describe("lettering note") {
    it("doesn't create a note from 246 .6 ǂa or 514 for visual material") {
      // This is based on b16529888
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("k")),
        varFields = List(
          VarField(marcTag = "514", subfields = List(Subfield(tag = "a", content = "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."))),
          VarField(
            marcTag = Some("246"),
            indicator2 = Some("6"),
            subfields = List(
              Subfield(tag = "a", content = "Le m\u00e9decin et la garde malade. H.D. ...")
            )
          )
        )
      )

      SierraNotes(bibData) shouldBe empty
    }

    it("uses 514 for non-visual material") {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("not-k")),
        varFields = List(
          VarField(marcTag = "514", subfields = List(Subfield(tag = "a", content = "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."))),
          VarField(
            marcTag = Some("246"),
            indicator2 = Some("6"),
            subfields = List(
              Subfield(tag = "a", content = "Le m\u00e9decin et la garde malade. H.D. ...")
            )
          )
        )
      )

      SierraNotes(bibData) shouldBe List(
        Note(
          noteType = NoteType.LetteringNote,
          contents = "Lettering continues: Comment va  le malade? H\\u00e9las Monsieur, il est mort ce matin \\u00e0 six heures! Ah il est mort le gaillard! .. Il n'a donc pas pris ma potion? Si Monsieur. Il en a donc trop pris? Non Monsieur. C'est qu'il n'en a assez pris. H.D."
        )
      )
    }
  }

  def bibData(contents: List[(String, Note)]): SierraBibData =
    bibData(contents.map { case (tag, note) => (tag, note.contents) }: _*)

  def bibData(contents: (String, String)*): SierraBibData =
    createSierraBibDataWith(
      varFields = contents.toList.map {
        case (tag, content) =>
          VarField(
            marcTag = tag,
            subfields = List(Subfield(tag = "a", content = content))
          )
      }
    )
}
