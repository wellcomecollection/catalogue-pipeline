package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{Concept, Place, Subject}
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

class SierraConceptSubjectsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  def bibId: SierraBibNumber = createSierraBibNumber

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

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        id = IdState.Identifiable(
          sourceIdentifier=SourceIdentifier(
            identifierType = IdentifierType.LabelDerived,
            value = "A Content",
            ontologyType = "Subject"
          )
        ),
        label = "A Content",
        concepts = List(Concept(label = "A Content"))
      )
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

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - V Content",
        concepts =
          List(Concept(label = "A Content"), Concept(label = "V Content"))
      )
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
    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - V Content",
        concepts =
          List(Concept(label = "A Content"), Concept(label = "V Content"))
      )
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

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - X Content - V Content",
        concepts = List(
          Concept(label = "A Content"),
          Concept(label = "X Content"),
          Concept(label = "V Content")
        )
      )
    )
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

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - Y Content",
        concepts = List(
          Concept(label = "A Content"),
          ParsedPeriod(label = "Y Content")
        )
      )
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
    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - Z Content",
        concepts = List(
          Concept(label = "A Content"),
          Place(label = "Z Content")
        )
      )
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

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A1 Content - Z1 Content",
        concepts = List(
          Concept(label = "A1 Content"),
          Place(label = "Z1 Content")
        )
      ),
      Subject(
        label = "A2 Content - V2 Content",
        concepts = List(
          Concept(label = "A2 Content"),
          Concept(label = "V2 Content")
        )
      )
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
