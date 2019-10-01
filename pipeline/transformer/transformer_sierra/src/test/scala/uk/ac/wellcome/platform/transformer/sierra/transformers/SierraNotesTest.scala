package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.{MarcSubfield, SierraBibData}

class SierraNotesTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("should extract notes from all fields") {
    val notes = List(
      "500" -> GeneralNote("note a"),
      "501" -> GeneralNote("note b"),
      "504" -> GeneralNote("note c"),
      "518" -> GeneralNote("note d"),
      "536" -> GeneralNote("note e"),
      "545" -> GeneralNote("note f"),
      "547" -> GeneralNote("note g"),
      "562" -> GeneralNote("note h"),
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
