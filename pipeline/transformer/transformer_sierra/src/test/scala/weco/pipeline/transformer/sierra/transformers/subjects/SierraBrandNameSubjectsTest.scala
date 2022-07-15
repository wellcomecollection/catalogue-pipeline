package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  SubjectMatchers
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraBrandNameSubjectsTest
    extends AnyFunSpec
    with Matchers
    with SubjectMatchers
    with ConceptMatchers
    with SierraDataGenerators {

  it("returns zero subjects if there are none") {
    getBrandNameSubjects(varFields = List()) shouldBe Nil
  }

  it("returns subjects for varfield 652") {
    val varFields = List(
      VarField(marcTag = "600", subfields = List(Subfield("a", "Not Content"))),
      VarField(marcTag = "652", subfields = List(Subfield("a", "Content"))),
    )
    val List(subject) = getBrandNameSubjects(varFields)
    subject should have(
      'label ("Content"),
      labelDerivedSubjectId("Content")
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("Content"),
      labelDerivedConceptId("Content")
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
    val subjects = getBrandNameSubjects(varFields)

    subjects.length shouldBe 2
    List("First", "Second").zip(subjects).map {
      case (label, subject) =>
        subject should have(
          'label (label),
          labelDerivedSubjectId(label)
        )
        val List(concept) = subject.concepts
        concept should have(
          'label (label),
          labelDerivedConceptId(label)
        )
    }
  }

  private def getBrandNameSubjects(varFields: List[VarField]) =
    SierraBrandNameSubjects(
      createSierraBibNumber,
      createSierraBibDataWith(varFields = varFields))
}
