package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  AbstractRootConcept,
  Concept,
  Period,
  Place
}
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  HasIdMatchers,
  SubjectMatchers
}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

class SierraConceptSubjectsTest
    extends AnyFunSpec
    with Matchers
    with ConceptMatchers
    with HasIdMatchers
    with SubjectMatchers
    with MarcGenerators
    with SierraDataGenerators
    with TableDrivenPropertyChecks {

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
    subject should have(
      'label ("A Content"),
      labelDerivedSubjectId("A Content")
    )

    val List(conceptA) = subject.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("A Content")
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
    subject should have(
      'label ("A Content - V Content"),
      labelDerivedSubjectId("A Content - V Content")
    )

    val List(conceptA, conceptV) = subject.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("A Content")
    )
    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("V Content")
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
    subject should have(
      'label ("A Content - V Content"),
      labelDerivedSubjectId("A Content - V Content")
    )

    val List(conceptA, conceptV) = subject.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("A Content")
    )
    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("V Content")
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
    subject should have(
      'label ("A Content - X Content - V Content"),
      labelDerivedSubjectId("A Content - X Content - V Content")
    )
    subject.concepts.length shouldBe 3
    subject.concepts.zip(List("A", "X", "V")).map {
      case (concept, capitalTag) =>
        concept shouldBe a[Concept[_]]
        concept should have(
          'label (s"$capitalTag Content"),
          labelDerivedConceptId(s"$capitalTag Content")
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
    subject should have(
      'label ("A Content - Y Content"),
      labelDerivedSubjectId("A Content - Y Content")
    )

    val List(conceptA, conceptY) = subject.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("A Content")
    )
    conceptY shouldBe a[Period[_]]
    conceptY should have(
      'label ("Y Content"),
      labelDerivedPeriodId("Y Content")
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
    subject should have(
      'label ("A Content - Z Content"),
      labelDerivedSubjectId("A Content - Z Content")
    )

    val List(conceptA, conceptY) = subject.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("A Content")
    )

    conceptY shouldBe a[Place[_]]
    conceptY should have(
      'label ("Z Content"),
      labelDerivedPlaceId("Z Content")
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

    val List(subject1, subject2) = SierraConceptSubjects(bibId, bibData)
    subject1.concepts.length shouldBe 2
    subject1 .concepts.head should have(
      'label("A1 Content"),
      labelDerivedConceptId("A1 Content")
    )

    subject1.concepts(1) should have(
      'label("Z1 Content"),
      sourceIdentifier(
        value="Z1 Content",
        ontologyType = "Place",
        identifierType = IdentifierType.LabelDerived
      )
    )

    subject2.concepts.length shouldBe 2
    subject2.concepts.head should have(
      'label("A2 Content"),
      labelDerivedConceptId("A2 Content")
    )
    subject2.concepts(1) should have(
      'label("V2 Content"),
      labelDerivedConceptId("V2 Content")
    )
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

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have(
      'label ("A Content - X Content - V Content"),
      labelDerivedSubjectId("A Content - X Content - V Content")
    )

    val List(conceptA, conceptX, conceptV) = subject.concepts
    conceptA shouldBe a[Period[_]]
    conceptA should have(
      'label ("A Content"),
      sourceIdentifier(
        value = "A Content",
        ontologyType = "Period",
        identifierType = IdentifierType.LabelDerived)
    )

    conceptX shouldBe a[Concept[_]]
    conceptX should have(
      'label ("X Content"),
      labelDerivedConceptId("X Content")
    )
    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("V Content")
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

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have(
      'label ("A Content - X Content - V Content"),
      labelDerivedSubjectId("A Content - X Content - V Content")
    )

    val List(conceptA, conceptX, conceptV) = subject.concepts
    conceptA shouldBe a[Place[_]]
    conceptA should have(
      'label ("A Content"),
      sourceIdentifier(
        value = "A Content",
        ontologyType = "Place",
        identifierType = IdentifierType.LabelDerived)
    )

    conceptX shouldBe a[Concept[_]]
    conceptX should have(
      'label ("X Content"),
      labelDerivedConceptId("X Content")
    )

    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("V Content")
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
    // TODO, I think the desired state is a bit more complex.
    // We ignore identified fields with second indicators other than 0 and 2
    // but if unidentified, we should process them (I think?)

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

    val List(subject) = SierraConceptSubjects(bibId, bibData)
    subject should have(
      'label ("abolition"),
      sourceIdentifier(
        value = "mesh/456",
        ontologyType = "Subject",
        identifierType = IdentifierType.MESH)
    )
    val List(concept) = subject.concepts

    concept should have(
      'label ("abolition"),
      sourceIdentifier(
        value = "mesh/456",
        ontologyType = "Concept",
        identifierType = IdentifierType.MESH)
    )
  }

  it("Ignores a subject with second indicator 7 but no subfield 0") {
    // TODO, this is not correct, the desired state is a bit more complex.
    // We ignore identified fields with second indicators other than 0 and 2
    // but if unidentified, we should process them (I think?)
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

  it(
    "removes a trailing period from a primary subject label, regardless of type") {
    // The different types of concept all normalise in their own fashion, removing
    // whatever flavour of terminal punctuation is peculiar to that tag.
    // However, when they are the Primary Concept, a terminal full stop is always removed
    forAll(
      Table(
        ("marcTag", "assertType"),
        (
          "648",
          (concept: AbstractRootConcept[Any]) => concept shouldBe a[Period[_]]),
        (
          "650",
          (concept: AbstractRootConcept[Any]) =>
            concept shouldBe a[Concept[_]]),
        (
          "651",
          (concept: AbstractRootConcept[Any]) => concept shouldBe a[Place[_]])
      )) { (marcTag, assertType) =>
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = marcTag,
            subfields = List(
              Subfield(tag = "a", content = "Diet, Food, and Nutrition.")
            )
          )
        )
      )

      val List(subject) = SierraConceptSubjects(bibId, bibData)
      subject should have(
        'label ("Diet, Food, and Nutrition"),
        labelDerivedSubjectId("Diet, Food, and Nutrition")
      )
      val concept = subject.onlyConcept
      assertType(concept)
      concept should have(
        'label ("Diet, Food, and Nutrition")
      )

    }
  }

  it("Assigns an extracted id to the sole Concept") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("650"),
          indicator2 = Some("0"),
          subfields = List(
            Subfield(tag = "a", content = "Medicine"),
            Subfield(tag = "0", content = "sh85083064")
          )
        )
      )
    )
    val List(subject) = SierraConceptSubjects(createSierraBibNumber, bibData)
    subject should have(
      'label ("Medicine"),
      sourceIdentifier(
        value = "sh85083064",
        ontologyType = "Subject",
        identifierType = IdentifierType.LCSubjects)
    )

    val List(concept) = subject.concepts
    concept should have(
      'label ("Medicine"),
      sourceIdentifier(
        value = "sh85083064",
        ontologyType = "Concept",
        identifierType = IdentifierType.LCSubjects)
    )
  }
  //TODO: Now that it's not doing the Mocky style test, we need to check that ParsedPeriod is being used.
  // put in a test with roman numeral dates and see what happens.
}
