package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield
import uk.ac.wellcome.platform.transformer.sierra.generators.{MarcGenerators, SierraDataGenerators}

class SierraConceptSubjectsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  def bibId = createSierraBibNumber

  it("returns zero subjects if there are none") {
    val bibData = createSierraBibDataWith(varFields = Nil)
    SierraConceptSubjects(bibId, bibData) shouldBe Nil
  }

  it("returns subjects for tag 650 with only subfield a") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content",
        concepts = List(Concept(label = "A Content"))
      )
    )

  }

  it("returns subjects for tag 650 with only subfields a and v") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "a", content = "A Content"),
            MarcSubfield(tag = "v", content = "V Content")
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
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "v", content = "V Content"),
            MarcSubfield(tag = "a", content = "A Content")
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
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "a", content = "A Content"),
            MarcSubfield(tag = "x", content = "X Content"),
            MarcSubfield(tag = "v", content = "V Content")
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
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "y", content = "Y Content"),
            MarcSubfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - Y Content",
        concepts = List(
          Concept(label = "A Content"),
          Period(label = "Y Content")
        )
      )
    )
  }

  it("returns subjects for tag 650 with subfields a, z") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "z", content = "Z Content"),
            MarcSubfield(tag = "a", content = "A Content")
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
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "a", content = "A1 Content"),
            MarcSubfield(tag = "z", content = "Z1 Content")
          )
        ),
        createVarFieldWith(
          marcTag = "650",
          subfields = List(
            MarcSubfield(tag = "a", content = "A2 Content"),
            MarcSubfield(tag = "v", content = "V2 Content")
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
        createVarFieldWith(
          marcTag = "648",
          subfields = List(
            MarcSubfield(tag = "a", content = "A Content"),
            MarcSubfield(tag = "x", content = "X Content"),
            MarcSubfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "A Content - X Content - V Content",
        concepts = List(
          Period(label = "A Content"),
          Concept(label = "X Content"),
          Concept(label = "V Content")
        )
      )
    )
  }

  it("returns subjects with primary concept Place for tag 651") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "651",
          subfields = List(
            MarcSubfield(tag = "x", content = "X Content"),
            MarcSubfield(tag = "a", content = "A Content"),
            MarcSubfield(tag = "v", content = "V Content")
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
            MarcSubfield(tag = "a", content = "absence"),
            MarcSubfield(tag = "0", content = "lcsh/123")
          )
        ),
        createVarFieldWith(
          marcTag = "650",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            MarcSubfield(tag = "a", content = "abolition"),
            MarcSubfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    val expectedSourceIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType("lc-subjects"),
        value = "lcsh/123",
        ontologyType = "Subject"
      ),
      SourceIdentifier(
        identifierType = IdentifierType("nlm-mesh"),
        value = "mesh/456",
        ontologyType = "Subject"
      )
    )

    val actualSourceIdentifiers = SierraConceptSubjects(bibId, bibData)
      .map(_.id)
      .map {
        case Identifiable(sourceIdentifier, _, _) => sourceIdentifier
        case other                                => assert(false, other)
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
            MarcSubfield(tag = "a", content = "absence"),
            MarcSubfield(tag = "0", content = "lcsh/123")
          )
        ),
        createVarFieldWith(
          marcTag = "650",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            MarcSubfield(tag = "a", content = "abolition"),
            MarcSubfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("nlm-mesh"),
      value = "mesh/456",
      ontologyType = "Subject"
    )

    SierraConceptSubjects(bibId, bibData) shouldBe List(
      Subject(
        label = "abolition",
        concepts = List(Concept("abolition")),
        id = Identifiable(sourceIdentifier)
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
            MarcSubfield(tag = "a", content = "abolition")
          )
        )
      )
    )

    SierraConceptSubjects(bibId, bibData) shouldBe Nil
  }
}
