package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import weco.catalogue.source_model.sierra.SierraBibData
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}

class SierraNotesTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("extracts notes from all fields") {
    val notes = List(
      "500" -> GeneralNote("general note a"),
      "501" -> GeneralNote("general note b"),
      "502" -> DissertationNote("dissertation note"),
      "504" -> BibliographicalInformation("bib info a"),
      "505" -> ContentsNote("contents note"),
      "508" -> CreditsNote("credits note a"),
      "510" -> ReferencesNote("references a"),
      "511" -> CreditsNote("credits note b"),
      "514" -> LetteringNote("Completeness:"),
      "518" -> TimeAndPlaceNote("time and place note"),
      "524" -> CiteAsNote("cite as note"),
      "533" -> ReproductionNote("reproduction a"),
      "534" -> ReproductionNote("reproduction b"),
      "535" -> LocationOfOriginalNote("location of original note"),
      "536" -> FundingInformation("funding information"),
      "540" -> TermsOfUse("terms of use"),
      "542" -> CopyrightNote("copyright a"),
      "545" -> BiographicalNote("bib info b"),
      "546" -> LanguageNote("Marriage certificate: German; Fraktur."),
      "547" -> GeneralNote("general note c"),
      "562" -> GeneralNote("general note d"),
      "563" -> BindingInformation("binding info note"),
      "581" -> PublicationsNote("publications b"),
      "585" -> ExhibitionsNote("exhibitions"),
      "586" -> AwardsNote("awards"),
      "591" -> GeneralNote("A general, unspecified note"),
      "593" -> CopyrightNote("copyright b"),
    )
    SierraNotes(bibData(notes)) shouldBe notes.map(_._2)
  }

  it("extracts all notes when duplicate fields") {
    val notes = List(
      "500" -> GeneralNote("note a"),
      "500" -> GeneralNote("note b"),
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
    val notes = List("500" -> GeneralNote("<p>note</p>"))
    SierraNotes(bibData(notes)) shouldBe notes.map(_._2)
  }

  it("concatenate all the subfields on a single MARC field into a single note") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "500",
          subfields = List(
            MarcSubfield(tag = "a", content = "1st part."),
            MarcSubfield(tag = "b", content = "2nd part."),
            MarcSubfield(tag = "c", content = "3rd part."),
          )
        )
      )
    )
    SierraNotes(bibData) shouldBe List(
      GeneralNote("1st part. 2nd part. 3rd part.")
    )
  }

  it("does not concatenate separate varfields") {
    val notes = List(
      "500" -> GeneralNote("1st note."),
      "500" -> GeneralNote("2nd note."),
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
            List(MarcSubfield(tag = "a", content = "The originals are in Oman"))
        ),
        createVarFieldWith(
          marcTag = "535",
          indicator1 = Some("2"),
          subfields = List(
            MarcSubfield(tag = "a", content = "The duplicates are in Denmark"))
        )
      )
    )

    SierraNotes(bibData) shouldBe List(
      LocationOfOriginalNote("The originals are in Oman"),
      LocationOfDuplicatesNote("The duplicates are in Denmark")
    )
  }

  it("only gets an ownership note if 561 1st indicator is 1") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("561"),
          indicator1 = Some("1"),
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Provenance: one plate in the set of plates"),
          )
        ),
        VarField(
          marcTag = Some("561"),
          indicator1 = Some("0"),
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Purchased from John Smith on 01/01/2001"),
          )
        ),
        VarField(
          marcTag = Some("561"),
          indicator1 = None,
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "Private contact details for John Smith"),
          )
        )
      )
    )
    SierraNotes(bibData) shouldBe List(
      OwnershipNote("Provenance: one plate in the set of plates")
    )
  }

  it("suppresses subfield ǂ5 universally") {
    val varFields = SierraNotes.notesFields.keys.map(key => {
      createVarFieldWith(
        marcTag = key,
        subfields = List(
          MarcSubfield(tag = "a", content = "Main bit."),
          MarcSubfield(tag = "5", content = "UkLW"),
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
            createVarFieldWith(
              marcTag = "000",
              subfields = List(
                MarcSubfield(tag = "a", content = "Main bit.")
              )
            )
        ))
      .toList

    SierraNotes(bibData) should contain theSameElementsAs notes
  }

  def bibData(contents: List[(String, Note)]): SierraBibData =
    bibData(contents.map { case (tag, note) => (tag, note.content) }: _*)

  def bibData(contents: (String, String)*): SierraBibData =
    createSierraBibDataWith(
      varFields = contents.toList.map {
        case (tag, content) =>
          createVarFieldWith(
            marcTag = tag,
            subfields = List(MarcSubfield(tag = "a", content = content))
          )
      }
    )
}
