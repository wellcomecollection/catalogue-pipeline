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
      "504" -> BibliographicalInformation("bib info a"),
      "505" -> ContentsNote("contents note"),
      "508" -> CreditsNote("credits note a"),
      "511" -> CreditsNote("credits note b"),
      "518" -> TimeAndPlaceNote("time and place note"),
      "536" -> FundingInformation("funding information"),
      "545" -> BibliographicalInformation("bib info b"),
      "547" -> GeneralNote("general note c"),
      "562" -> GeneralNote("general note d"),
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
      "502" -> "not a note",
    )
    SierraNotes(bibId, bibData(notes: _*)) shouldBe Nil
  }

  it("should preserve html in notes fields") {
    val notes = List("500" -> GeneralNote("<p>note</p>"))
    SierraNotes(bibId, bibData(notes)) shouldBe notes.map(_._2)
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
