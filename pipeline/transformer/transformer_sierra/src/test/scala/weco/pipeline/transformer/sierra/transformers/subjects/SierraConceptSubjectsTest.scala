package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Concept, Place, Subject, Period}
import weco.pipeline.transformer.sierra.transformers.matchers.{ConceptsMatchers, SourceIdentifierMatchers, SubjectMatchers}
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

class SierraConceptSubjectsTest
    extends AnyFunSpec with Matchers
    with SourceIdentifierMatchers
    with ConceptsMatchers
    with SubjectMatchers
    with MarcGenerators
    with SierraDataGenerators {

  private def bibId: SierraBibNumber = createSierraBibNumber

  it("returns zero subjects if there are none") {
    val bibData = createSierraBibDataWith(varFields = Nil)
    SierraConceptSubjects(bibId, bibData) shouldBe Nil
  }

  it("returns subjects for tag 650 with only subfield a") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have (
      subjectLabel( "A Content"),
      labelDerivedSubjectId("A Content")
    )

    val List(conceptA) = subject.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      conceptLabel ("A Content"),
      labelDerivedConceptId ("A Content")
    )
  }

  it("returns subjects for tag 650 with only subfields a and v") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "a", content = "A Content"),
            Subfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have (
      subjectLabel( "A Content - V Content"),
      labelDerivedSubjectId("A Content - V Content")
    )

    val List(conceptA, conceptV) = subject.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      conceptLabel ("A Content"),
      labelDerivedConceptId ("A Content")
    )
    conceptV shouldBe a [Concept[_]]
    conceptV should have (
      conceptLabel ("V Content"),
      labelDerivedConceptId ("V Content")
    )
  }

  it(
    "subfield a is always first concept when returning subjects for tag 650 with subfields a, v") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "v", content = "V Content"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have (
      subjectLabel( "A Content - V Content"),
      labelDerivedSubjectId("A Content - V Content")
    )

    val List(conceptA, conceptV) = subject.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      conceptLabel ("A Content"),
      labelDerivedConceptId ("A Content")
    )
    conceptV shouldBe a [Concept[_]]
    conceptV should have (
      conceptLabel ("V Content"),
      labelDerivedConceptId ("V Content")
    )
  }

  it("returns subjects for tag 650 subfields a, v, and x") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "a", content = "A Content"),
            Subfield(tag = "x", content = "X Content"),
            Subfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have (
      subjectLabel( "A Content - X Content - V Content"),
      labelDerivedSubjectId("A Content - X Content - V Content")
    )
    subject.concepts.length shouldBe 3
    subject.concepts.zip(List("A", "X", "V")).map {
      case (concept, capitalTag) =>
        concept shouldBe a [Concept[_]]
        concept should have (
          conceptLabel (s"$capitalTag Content"),
          labelDerivedConceptId (s"$capitalTag Content")
        )
    }
  }

  it("returns subjects for tag 650 with subfields a, y") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "y", content = "Y Content"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have (
      subjectLabel( "A Content - Y Content"),
      labelDerivedSubjectId("A Content - Y Content")
    )

    val List(conceptA, conceptY) = subject.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      conceptLabel ("A Content"),
      labelDerivedConceptId ("A Content")
    )
    conceptY shouldBe a [Period[_]]
    conceptY should have (
      conceptLabel ("Y Content"),
      labelDerivedConceptId ("Y Content")
    )
  }

  it("returns subjects for tag 650 with subfields a, z") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "z", content = "Z Content"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have (
      subjectLabel( "A Content - Z Content"),
      labelDerivedSubjectId("A Content - Z Content")
    )

    val List(conceptA, conceptY) = subject.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      conceptLabel ("A Content"),
      labelDerivedConceptId ("A Content")
    )

    conceptY shouldBe a [Place[_]]
    conceptY should have (
      conceptLabel ("Z Content"),
      labelDerivedConceptId ("Z Content")
    )
  }

  it("returns subjects for multiple 650 tags with different subfields") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "a", content = "A1 Content"),
            Subfield(tag = "z", content = "Z1 Content")
          )
        ),
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "a", content = "A2 Content"),
            Subfield(tag = "v", content = "V2 Content")
          )
        )
      )
    )

    val subjects = SierraConceptSubjects(bibId, bibData)
    subjects.length shouldBe 2
    subjects.zip(
      List(
        ("A1 Content - Z1 Content", "A1 Content", "Z1 Content"),
        ("A2 Content - V2 Content", "A2 Content", "V2 Content"),
      )
    ).map {
      case (subject, (expectedSubjectLabel, concept1Label, concept2Label)) =>
        subject should have (
          subjectLabel( expectedSubjectLabel),
          labelDerivedSubjectId(expectedSubjectLabel)
        )
        val List(concept1, concept2) = subject.concepts
        concept1 should have (
          conceptLabel (concept1Label),
          labelDerivedConceptId (concept1Label)
        )
        concept2 should have (
          conceptLabel (concept2Label),
          labelDerivedConceptId (concept2Label)
        )
    }
  }

  it("returns subjects with primary concept Period for tag 648") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "648",
          subfields = List(
            Subfield(tag = "a", content = "A Content"),
            Subfield(tag = "x", content = "X Content"),
            Subfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - X Content - V Content",
        concepts = List(
          ParsedPeriod(label = "A Content"),
          Concept(label = "X Content"),
          Concept(label = "V Content")
        )
      )
    )
  }

  it("returns subjects with primary concept Place for tag 651") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "651",
          subfields = List(
            Subfield(tag = "x", content = "X Content"),
            Subfield(tag = "a", content = "A Content"),
            Subfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - X Content - V Content",
        concepts = List(
          Place(label = "A Content"),
          Concept(label = "X Content"),
          Concept(label = "V Content")
        )
      )
    )
  }

  it(s"gets identifiers from subfield $$0") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "650",
          // LCSH heading
          indicator2 = "0",
          subfields = List(
            Subfield(tag = "a", content = "absence"),
            Subfield(tag = "0", content = "lcsh/123")
          )
        ),
        createVarFieldWith(
          marcTag = "650",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            Subfield(tag = "a", content = "abolition"),
            Subfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    val expectedSourceIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType.LCSubjects,
        value = "lcsh/123",
        ontologyType = "Subject"
      ),
      SourceIdentifier(
        identifierType = IdentifierType.MESH,
        value = "mesh/456",
        ontologyType = "Subject"
      )
    )

    val actualSourceIdentifiers = SierraConceptSubjects(bibId, bibData)
      .map(_.id)
      .map {
        case IdState.Identifiable(sourceIdentifier, _, _) => sourceIdentifier
        case other                                        => assert(false, other)
      }

    expectedSourceIdentifiers shouldBe actualSourceIdentifiers
  }

  it("ignores subject with second indicator 7") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "650",
          indicator2 = "7",
          subfields = List(
            Subfield(tag = "a", content = "absence"),
            Subfield(tag = "0", content = "lcsh/123")
          )
        ),
        createVarFieldWith(
          marcTag = "650",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            Subfield(tag = "a", content = "abolition"),
            Subfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = "Subject"
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "abolition",
        concepts = List(Concept("abolition")),
        id = IdState.Identifiable(sourceIdentifier)
      )
    )
  }

  it("Ignores a subject with second indicator 7 but no subfield 0") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "650",
          indicator2 = "7",
          subfields = List(
            Subfield(tag = "a", content = "abolition")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe Nil
  }

  it("removes a trailing period from a subject label") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "650",
          subfields = List(
            Subfield(tag = "a", content = "Diet, Food, and Nutrition.")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        "Diet, Food, and Nutrition",
        concepts = List(Concept("Diet, Food, and Nutrition"))))
  }
}
