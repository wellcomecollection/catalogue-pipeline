package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdentifierType
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

    // It is a happy accident of history that Subjects derived from Meetings have an ontologyType
    // of "Meeting", rather than "Subject", as other Subjects do.
    // It is likely that in the near future, we will remove the identifier from Subject, and leave it
    // simply up to the concepts that define it, but for now, we will ensure that it exists.
    val List(subject) = SierraMeetingSubjects(bibId, data)
    subject should have(
      'label ("Content"),
      sourceIdentifier(
        value = "content",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LabelDerived)
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("Content"),
      labelDerivedMeetingId("content")
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
      sourceIdentifier(
        value = "c a d",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LabelDerived)
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("C A D"),
      sourceIdentifier(
        value = "c a d",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LabelDerived)
    )
  }

  it("returns a lowercase ascii normalised identifier") {
    val data = bibData(
      varField(
        "611",
        Subfield(tag = "a", content = "Düsseldorf Convention 2097"),
      )
    )

    val List(subject) = SierraMeetingSubjects(bibId, data)
    subject should have(
      'label ("Düsseldorf Convention 2097"),
      sourceIdentifier(
        value = "dusseldorf convention 2097",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LabelDerived)
    )
    val List(concept) = subject.concepts
    concept should have(
      'label ("Düsseldorf Convention 2097"),
      sourceIdentifier(
        value = "dusseldorf convention 2097",
        ontologyType = "Meeting",
        identifierType = IdentifierType.LabelDerived)
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
          sourceIdentifier(
            value = label.toLowerCase,
            ontologyType = "Meeting",
            identifierType = IdentifierType.LabelDerived)
        )
        val List(concept) = subject.concepts
        concept should have(
          'label (label),
          sourceIdentifier(
            value = label.toLowerCase,
            ontologyType = "Meeting",
            identifierType = IdentifierType.LabelDerived)
        )
    }
  }

}
