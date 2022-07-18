package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  HasIdMatchers
}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraMeetingSubjectsTest
    extends AnyFunSpec
    with Matchers
    with ConceptMatchers
    with HasIdMatchers
    with MarcGenerators
    with SierraDataGenerators {

  private def bibId = createSierraBibNumber

  private def bibData(varFields: VarField*) =
    createSierraBibDataWith(varFields = varFields.toList)

  private def varField(tag: String, subfields: Subfield*) =
    createVarFieldWith(
      marcTag = tag,
      subfields = subfields.toList,
      indicator2 = "0")

  it("returns zero subjects if there are none") {
    SierraMeetingSubjects(bibId, bibData()) shouldBe Nil
  }

  it("returns zero subjects if subfields all $$a, $$c and $$d are missing") {
    val data = bibData(
      varField("611", Subfield(tag = "x", content = "Hmmm"))
    )
    SierraMeetingSubjects(bibId, data) shouldBe Nil
  }

  it("returns subjects for varfield 611, subfield $$a") {
    val data = bibData(
      varField("600", Subfield(tag = "a", content = "Not content")),
      varField("611", Subfield(tag = "a", content = "Content")),
    )

    val List(subject) = SierraMeetingSubjects(bibId, data)
    subject should have(
      'label ("Content"),
      'id (IdState.Unidentifiable)
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("Content"),
      labelDerivedMeetingId("Content")
    )

  }

  it(
    "returns subjects with subfields $$a, $$c and $$d concatenated in order they appear") {
    val data = bibData(
      varField(
        "611",
        Subfield(tag = "c", content = "C"),
        Subfield(tag = "a", content = "A"),
        Subfield(tag = "d", content = "D"),
      )
    )

    val List(subject) = SierraMeetingSubjects(bibId, data)
    subject should have(
      'label ("C A D"),
      'id (IdState.Unidentifiable)
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("C A D"),
      labelDerivedMeetingId("C A D")
    )
  }

  it("creates an identifiable subject if subfield 0") {
    val data = bibData(
      varField(
        "611",
        Subfield(tag = "a", content = "Content"),
        Subfield(tag = "0", content = "lcsh7212")
      )
    )

    val List(subject) = SierraMeetingSubjects(bibId, data)
    subject should have(
      'label ("Content"),
      sourceIdentifier(
        value = "lcsh7212",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LCNames)
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("Content"),
      sourceIdentifier(
        value = "lcsh7212",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LCNames)
    )

  }

  it("returns multiple subjects if multiple 611") {
    val data = bibData(
      varField("611", Subfield(tag = "a", content = "First")),
      varField("611", Subfield(tag = "a", content = "Second")),
    )
    val subjects = SierraMeetingSubjects(bibId, data)
    subjects.length shouldBe 2
    List("First", "Second").zip(subjects).map {
      case (label, subject) =>
        // As we are planning to get rid of Subject ids,
        // only those places that did have them should keep them
        // They should not be introduced anywhere else.
        subject should have(
          'label (label),
          'id (IdState.Unidentifiable)
        )
        val List(concept) = subject.concepts
        concept should have(
          'label (label),
          sourceIdentifier(
            value = label,
            ontologyType = "Meeting",
            identifierType = IdentifierType.LabelDerived)
        )
    }
  }
}
