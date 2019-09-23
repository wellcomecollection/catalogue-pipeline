package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraNotesTest
  extends FunSpec
  with Matchers
  with MarcGenerators
  with SierraDataGenerators {

  it("should extract notes from all fields") {
    val notes = List(
      "500" -> "note a",
      "501" -> "note b",
      "504" -> "note c",
      "518" -> "note d",
      "536" -> "note e",
      "545" -> "note f",
      "547" -> "note g",
      "562" -> "note h",
    )
    SierraNotes(bibId, bibData(notes:_*)) shouldBe notes.map(_._2)
  }

  it("should extract all notes when duplicate fields") {
    val notes = List(
      "500" -> "note a",
      "500" -> "note b",
    )
    SierraNotes(bibId, bibData(notes:_*)) shouldBe notes.map(_._2)
  }

  it("should not extract notes from non notes fields") {
    val notes = List(
      "100" -> "not a note",
      "502" -> "not a note",
    )
    SierraNotes(bibId, bibData(notes:_*)) shouldBe Nil
  }

  it("should preserve html in notes fields") {
    val note = "500" -> "<p>note</p>"
    SierraNotes(bibId, bibData(note)) shouldBe List(note._2)
  }

  def bibId = createSierraBibNumber

  def bibData(notes: (String, String)*) =
      createSierraBibDataWith(
        varFields =
          notes.toList.map { case (tag, value) =>
            createVarFieldWith(marcTag = tag, content = Some(value))
          }
      )
}
