package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraBrandNameSubjectsTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  def bibId = createSierraBibNumber

  def bibData(varFields: VarField*) =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(tag: String, content: Option[String], subfields: MarcSubfield*) =
    createVarFieldWith(
      marcTag = tag,
      content = content,
      subfields = subfields.toList)

  it("returns zero subjects if there are none") {
    SierraBrandNameSubjects(bibId, bibData()) shouldBe Nil
  }

  it("returns subjects for varfield 652") {
    val data = bibData(
      varField("600", Some("Not Content")),
      varField("652", Some("Content")),
    )
    SierraBrandNameSubjects(bibId, data) shouldBe List(
      Unidentifiable(
        Subject(
          label = "Content",
          concepts = List(Unidentifiable(Concept(label = "Content")))
        )
      )
    )
  }

  it("does not used subfields to parse the content") {
    val data = bibData(
      varField("652", None, MarcSubfield(tag = "x", content = "Hmmm"))
    )
    SierraBrandNameSubjects(bibId, data) shouldBe Nil
  }

  it("returns multiple subjects if multiple 652") {
    val data = bibData(
      varField("652", Some("First")),
      varField("652", Some("Second")),
    )
    SierraBrandNameSubjects(bibId, data) shouldBe List(
      Unidentifiable(
        Subject(
          label = "First",
          concepts = List(Unidentifiable(Concept(label = "First")))
        )
      ),
      Unidentifiable(
        Subject(
          label = "Second",
          concepts = List(Unidentifiable(Concept(label = "Second")))
        )
      )
    )
  }
}
