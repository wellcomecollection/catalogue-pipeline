package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{MarcSubfield, VarField}
import uk.ac.wellcome.platform.transformer.sierra.generators.{MarcGenerators, SierraDataGenerators}

class SierraMeetingSubjectsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  def bibId = createSierraBibNumber

  def bibData(varFields: VarField*) =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(tag: String, subfields: MarcSubfield*) =
    createVarFieldWith(
      marcTag = tag,
      subfields = subfields.toList,
      indicator2 = "0")

  it("returns zero subjects if there are none") {
    SierraMeetingSubjects(bibId, bibData()) shouldBe Nil
  }

  it("returns subjects for varfield 611, subfield $$a") {
    val data = bibData(
      varField("600", MarcSubfield(tag = "a", content = "Not content")),
      varField("611", MarcSubfield(tag = "a", content = "Content")),
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
        MarcSubfield(tag = "c", content = "C"),
        MarcSubfield(tag = "a", content = "A"),
        MarcSubfield(tag = "d", content = "D"),
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
      varField("611", MarcSubfield(tag = "x", content = "Hmmm"))
    )
    SierraMeetingSubjects(bibId, data) shouldBe Nil
  }

  it("creates an identifiable subject if subfield 0") {
    val data = bibData(
      varField(
        "600",
        MarcSubfield(tag = "a", content = "Content"),
        MarcSubfield(tag = "0", content = "lcsh7212")
      )
    )
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("lc-names"),
      ontologyType = "Subject",
      value = "lcsh7212"
    )
    SierraPersonSubjects(bibId, data) shouldBe List(
      Subject(
        id = Identifiable(sourceIdentifier),
        label = "Content",
        concepts = List(Person(label = "Content"))
      )
    )
  }

  it("returns multiple subjects if multiple 611") {
    val data = bibData(
      varField("611", MarcSubfield(tag = "a", content = "First")),
      varField("611", MarcSubfield(tag = "a", content = "Second")),
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
