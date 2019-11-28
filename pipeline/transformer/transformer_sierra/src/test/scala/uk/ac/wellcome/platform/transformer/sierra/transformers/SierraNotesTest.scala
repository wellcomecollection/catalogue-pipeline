package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData
}

class SierraNotesTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("should extract notes from all fields") {
    val notes = List(
      "500" -> GeneralNote("general note a"),
      "501" -> GeneralNote("general note b"),
      "502" -> DissertationNote("dissertation note"),
      "504" -> BibliographicalInformation("bib info a"),
      "505" -> ContentsNote("contents note"),
      "508" -> CreditsNote("credits note a"),
      "511" -> CreditsNote("credits note b"),
      "518" -> TimeAndPlaceNote("time and place note"),
      "524" -> CiteAsNote("cite as note"),
      "535" -> LocationOfOriginalNote("location of original note"),
      "536" -> FundingInformation("funding information"),
      "545" -> BibliographicalInformation("bib info b"),
      "547" -> GeneralNote("general note c"),
      "562" -> GeneralNote("general note d"),
      "563" -> BindingInformation("binding info note"),
    )
    SierraNotes(bibId, bibData(notes)) shouldBe notes.map(_._2)
  }

  it("should extract all notes when duplicate fields") {
    val notes = List(
      "500" -> GeneralNote("note a"),
      "500" -> GeneralNote("note b"),
    )
    SierraNotes(bibId, bibData(notes)) shouldBe notes.map(_._2)
  }

  it("should not extract notes from non notes fields") {
    val notes = List(
      "100" -> "not a note",
      "530" -> "not a note",
    )
    SierraNotes(bibId, bibData(notes: _*)) shouldBe Nil
  }

  it("should preserve html in notes fields") {
    val notes = List("500" -> GeneralNote("<p>note</p>"))
    SierraNotes(bibId, bibData(notes)) shouldBe notes.map(_._2)
  }

  it("should concatenate subfields into a single note") {
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
    SierraNotes(bibId, bibData) shouldBe List(
      GeneralNote("1st part. 2nd part. 3rd part.")
    )
  }

  it("should not concatenate seperate varfields") {
    val notes = List(
      "500" -> GeneralNote("1st note."),
      "500" -> GeneralNote("2nd note."),
    )
    SierraNotes(bibId, bibData(notes)) shouldBe notes.map(_._2)
  }

  it("should supress subfield $5 in binding information") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "563",
          subfields = List(
            MarcSubfield(tag = "a", content = "Main bit."),
            MarcSubfield(tag = "5", content = "UkLW"),
          )
        )
      )
    )
    SierraNotes(bibId, bibData) shouldBe List(
      BindingInformation("Main bit.")
    )
  }

  def bibId = createSierraBibNumber

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
