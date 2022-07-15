package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{Concept, Genre, Place}
import weco.pipeline.transformer.sierra.transformers.matchers.ConceptMatchers
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraGenresTest
    extends AnyFunSpec
    with Matchers
      with ConceptMatchers
    with MarcGenerators
    with SierraDataGenerators {

  it("returns zero genres if there are none") {
    val bibData = createSierraBibDataWith(varFields = List())
    SierraGenres(bibData) shouldBe Nil
  }

  it("returns genres for tag 655 with only subfield a") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content",
          concepts = List(
            Concept(
              id = IdState.Identifiable(
                sourceIdentifier = SourceIdentifier(
                  identifierType = IdentifierType.LabelDerived,
                  value = "A Content",
                  ontologyType = "Concept"
                )
              ),
              label = "A Content"
            )
          )
        ))

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(Subfield(tag = "a", content = "A Content"))
        )
      )
    )

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it("returns subjects for tag 655 with subfields a and v") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "A Content"),
            Subfield(tag = "v", content = "V Content")
          )
        )
      )
    )
    val List(genre) = SierraGenres(bibData)

    genre should have (
      'label ("A Content - V Content")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      'label ("A Content"),
      labelDerivedConceptId ("A Content")
    )
    conceptV shouldBe a [Concept[_]]
    conceptV should have (
      'label ("V Content"),
      labelDerivedConceptId ("V Content")
    )
  }

  it(
    "subfield a is always first concept when returning subjects for tag 655 with subfields a, v") {

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "v", content = "V Content"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    val List(genre) = SierraGenres(bibData)

    genre should have (
      'label ("A Content - V Content")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      'label ("A Content"),
      labelDerivedConceptId ("A Content")
    )
    conceptV shouldBe a [Concept[_]]
    conceptV should have (
      'label ("V Content"),
      labelDerivedConceptId ("V Content")
    )
  }

  it("returns genres for tag 655 subfields a, v, and x") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "A Content"),
            Subfield(tag = "x", content = "X Content"),
            Subfield(tag = "v", content = "V Content")
          )
        )
      )
    )


    val List(genre) = SierraGenres(bibData)

    genre should have (
      'label ("A Content - X Content - V Content")
    )

    val List(conceptA, conceptX, conceptV) = genre.concepts
    conceptA shouldBe a [Concept[_]]
    conceptA should have (
      'label ("A Content"),
      labelDerivedConceptId ("A Content")
    )
    conceptX shouldBe a [Concept[_]]
    conceptX should have (
      'label ("X Content"),
      labelDerivedConceptId ("X Content")
    )
    conceptV shouldBe a [Concept[_]]
    conceptV should have (
      'label ("V Content"),
      labelDerivedConceptId ("V Content")
    )
  }

  it("returns subjects for tag 655 with subfields a, y") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content - Y Content",
          concepts = List(
            Concept(label = "A Content"),
            ParsedPeriod(label = "Y Content")
          )))

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "y", content = "Y Content"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it("returns subjects for tag 655 with subfields a, z") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content - Z Content",
          concepts = List(
            Concept(label = "A Content"),
            Place(label = "Z Content")
          )))

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "z", content = "Z Content"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it("deduplicates transformed genres") {
    // This is based on bib b25028042 as of 23 January 2021
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "Electronic journals")
          )
        ),
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "Electronic journals")
          )
        ),
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "Periodical")
          )
        ),
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "Periodicals"),
            Subfield(tag = "2", content = "rbgenr")
          )
        ),
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "Periodicals"),
            Subfield(tag = "2", content = "lcgft")
          )
        ),
      )
    )

    val expectedGenres =
      List("Electronic journals", "Periodical", "Periodicals")
        .map { label =>
          Genre(
            label = label,
            concepts = List(
              Concept(label = label),
            )
          )
        }

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it("returns subjects for multiple 655 tags with different subfields") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "A1 Content"),
            Subfield(tag = "z", content = "Z1 Content")
          )
        ),
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "A2 Content"),
            Subfield(tag = "v", content = "V2 Content")
          )
        )
      )
    )

    val expectedSubjects =
      List(
        Genre(
          label = "A1 Content - Z1 Content",
          concepts = List(
            Concept(label = "A1 Content"),
            Place(label = "Z1 Content")
          )),
        Genre(
          label = "A2 Content - V2 Content",
          concepts = List(
            Concept(label = "A2 Content"),
            Concept(label = "V2 Content")
          ))
      )
    SierraGenres(bibData) shouldBe expectedSubjects
  }

  it("strips punctuation from Sierra genres") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "a", content = "Printed books.")
          )
        )
      )
    )

    val expectedSubjects =
      List(
        Genre(
          label = "Printed books",
          concepts = List(
            Concept(label = "Printed books")
          ))
      )
    SierraGenres(bibData) shouldBe expectedSubjects
  }

  it(s"gets identifiers from subfield $$0") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "655",
          // LCSH heading
          indicator2 = "0",
          subfields = List(
            Subfield(tag = "a", content = "absence"),
            Subfield(tag = "0", content = "lcsh/123")
          )
        ),
        createVarFieldWith(
          marcTag = "655",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            Subfield(tag = "a", content = "abolition"),
            Subfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    // TODO Get rid of the check method??

    val expectedSourceIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType.LCSubjects,
        value = "lcsh/123",
        ontologyType = "Concept"
      ),
      SourceIdentifier(
        identifierType = IdentifierType.MESH,
        value = "mesh/456",
        ontologyType = "Concept"
      )
    )

    val actualSourceIdentifiers = SierraGenres(bibData)
      .map { _.concepts.head.id }
      .map {
        case IdState.Identifiable(sourceIdentifier, _, _) =>
          sourceIdentifier
        case other => assert(false, other)
      }

    expectedSourceIdentifiers shouldBe actualSourceIdentifiers
  }

  it(s"creates a label-derived identifier for concepts with no identifier") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "655",
          // LCSH heading
          indicator2 = "0",
          subfields = List(
            Subfield(tag = "a", content = "absence"),
          )
        ),
        createVarFieldWith(
          marcTag = "655",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            Subfield(tag = "a", content = "abolition"),
          )
        )
      )
    )

    val expectedSourceIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = "absence",
        ontologyType = "Concept"
      ),
      SourceIdentifier(
        identifierType = IdentifierType.LabelDerived,
        value = "abolition",
        ontologyType = "Concept"
      )
    )

    val actualSourceIdentifiers = SierraGenres(bibData)
      .map { _.concepts.head.id }
      .map {
        case IdState.Identifiable(sourceIdentifier, _, _) =>
          sourceIdentifier
        case other => assert(false, other)
      }

    expectedSourceIdentifiers shouldBe actualSourceIdentifiers
  }
}
