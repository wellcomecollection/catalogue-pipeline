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

  def varField(tag: String, subfields: MarcSubfield*) =
    createVarFieldWith(marcTag = tag, subfields = subfields.toList)

  it("returns zero subjects if there are none") {
    SierraBrandNameSubjects(bibId, bibData()) shouldBe Nil
  }

  it("returns subjects for varfield 652") {
    val data = bibData(
      varField("600", MarcSubfield("a", "Not Content")),
      varField("652", MarcSubfield("a", "Content")),
    )
    SierraBrandNameSubjects(bibId, data) shouldBe List(
      Subject(
        label = "Content",
        concepts = List(Concept(label = "Content"))
      )
    )
  }

  it("does not used non 'a' subfields to parse the content") {
    val data = bibData(
      varField("652", MarcSubfield(tag = "b", content = "Hmmm"))
    )
    SierraBrandNameSubjects(bibId, data) shouldBe Nil
  }

  it("returns multiple subjects if multiple 652") {
    val data = bibData(
      varField("652", MarcSubfield("a", "First")),
      varField("652", MarcSubfield("a", "Second")),
    )
    SierraBrandNameSubjects(bibId, data) shouldBe List(
      Subject(
        label = "First",
        concepts = List(Concept(label = "First"))
      ),
      Subject(
        label = "Second",
        concepts = List(Concept(label = "Second"))
      )
    )
  }
}
