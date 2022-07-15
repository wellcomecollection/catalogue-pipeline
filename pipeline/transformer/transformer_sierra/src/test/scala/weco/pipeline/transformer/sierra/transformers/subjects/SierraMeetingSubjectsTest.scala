package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Meeting, Person, Subject}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraMeetingSubjectsTest
    extends AnyFunSpec
    with Matchers
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

  it("returns subjects for varfield 611, subfield $$a") {
    val data = bibData(
      varField("600", Subfield(tag = "a", content = "Not content")),
      varField("611", Subfield(tag = "a", content = "Content")),
    )
    SierraMeetingSubjects(bibId, data) shouldBe List(
      Subject(
        label = "Content",
        concepts = List(Meeting(label = "Content"))
      )
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
    SierraMeetingSubjects(bibId, data) shouldBe List(
      Subject(
        label = "C A D",
        concepts = List(Meeting(label = "C A D"))
      )
    )
  }

  it("returns zero subjects if subfields all $$a, $$c and $$d are missing") {
    val data = bibData(
      varField("611", Subfield(tag = "x", content = "Hmmm"))
    )
    SierraMeetingSubjects(bibId, data) shouldBe Nil
  }

  it("creates an identifiable subject if subfield 0") {
    val data = bibData(
      varField(
        "600",
        Subfield(tag = "a", content = "Content"),
        Subfield(tag = "0", content = "lcsh7212")
      )
    )
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LCNames,
      ontologyType = "Subject",
      value = "lcsh7212"
    )
    SierraPersonSubjects(bibId, data) shouldBe List(
      Subject(
        id = IdState.Identifiable(sourceIdentifier),
        label = "Content",
        concepts = List(Person(label = "Content"))
      )
    )
  }

  it("returns multiple subjects if multiple 611") {
    val data = bibData(
      varField("611", Subfield(tag = "a", content = "First")),
      varField("611", Subfield(tag = "a", content = "Second")),
    )
    SierraMeetingSubjects(bibId, data) shouldBe List(
      Subject(
        label = "First",
        concepts = List(Meeting(label = "First"))
      ),
      Subject(
        label = "Second",
        concepts = List(Meeting(label = "Second"))
      )
    )
  }
}
