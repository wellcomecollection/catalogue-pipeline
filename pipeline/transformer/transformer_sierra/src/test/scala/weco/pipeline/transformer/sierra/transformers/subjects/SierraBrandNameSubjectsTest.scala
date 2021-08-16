package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraBrandNameSubjectsTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  it("returns zero subjects if there are none") {
    getBrandNameSubjects(varFields = List()) shouldBe Nil
  }

  it("returns subjects for varfield 652") {
    val varFields = List(
      VarField(marcTag = "600", subfields = List(Subfield("a", "Not Content"))),
      VarField(marcTag = "652", subfields = List(Subfield("a", "Content"))),
    )

    getBrandNameSubjects(varFields) shouldBe List(
      Subject(
        label = "Content",
        concepts = List(Concept(label = "Content"))
      )
    )
  }

  it("does not used non 'a' subfields to parse the content") {
    val varFields = List(
      VarField(
        marcTag = "652",
        subfields = List(Subfield(tag = "b", content = "Hmmm")))
    )

    getBrandNameSubjects(varFields) shouldBe Nil
  }

  it("returns multiple subjects if multiple 652") {
    val varFields = List(
      VarField(marcTag = "652", subfields = List(Subfield("a", "First"))),
      VarField(marcTag = "652", subfields = List(Subfield("a", "Second")))
    )

    getBrandNameSubjects(varFields) shouldBe List(
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

  private def getBrandNameSubjects(varFields: List[VarField]) =
    SierraBrandNameSubjects(
      createSierraBibNumber,
      createSierraBibDataWith(varFields = varFields))
}
