package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  Concept,
  Genre,
  InstantRange,
  Period,
  Place
}
import weco.pipeline.transformer.sierra.transformers.matchers._
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

import java.time.LocalDate

class SierraGenresTest
    extends AnyFunSpec
    with Matchers
    with ConceptMatchers
    with HasIdMatchers
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
                  value = "a content",
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

    genre should have(
      'label ("A Content - V Content")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("a content")
    )
    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("v content")
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

    genre should have(
      'label ("A Content - V Content")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("a content")
    )
    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("v content")
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

    genre should have(
      'label ("A Content - X Content - V Content")
    )

    val List(conceptA, conceptX, conceptV) = genre.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("a content")
    )
    conceptX shouldBe a[Concept[_]]
    conceptX should have(
      'label ("X Content"),
      labelDerivedConceptId("x content")
    )
    conceptV shouldBe a[Concept[_]]
    conceptV should have(
      'label ("V Content"),
      labelDerivedConceptId("v content")
    )
  }

  it("returns subjects for tag 655 with subfields a, y") {

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "655",
          subfields = List(
            Subfield(tag = "y", content = "MDCCLXXXVII. [1787]"),
            Subfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    val List(genre) = SierraGenres(bibData)

    genre should have(
      'label ("A Content - MDCCLXXXVII. [1787]")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("a content")
    )
    conceptV shouldBe a[Period[_]]
    conceptV should have(
      'label ("MDCCLXXXVII. [1787]"),
      labelDerivedPeriodId("1787"),
      'range (
        Some(
          InstantRange(
            LocalDate of (1787, 1, 1),
            LocalDate of (1787, 12, 31),
            "MDCCLXXXVII. [1787]")))
    )
  }

  it("returns subjects for tag 655 with subfields a, z") {
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

    val List(genre) = SierraGenres(bibData)

    genre should have(
      'label ("A Content - Z Content")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a[Concept[_]]
    conceptA should have(
      'label ("A Content"),
      labelDerivedConceptId("a content")
    )
    conceptV shouldBe a[Place[_]]
    conceptV should have(
      'label ("Z Content"),
      labelDerivedPlaceId("z content")
    )
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

    val genres = SierraGenres(bibData)
    List("Electronic journals", "Periodical", "Periodicals").zip(genres).map {
      case (genreName, genre) =>
        genre should have(
          'label (genreName)
        )

        val List(concept) = genre.concepts
        concept shouldBe a[Concept[_]]
        concept should have(
          'label (genreName),
          labelDerivedConceptId(genreName.toLowerCase())
        )
    }
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

    val List(genre1, genre2) = SierraGenres(bibData)
    genre1.concepts.length shouldBe 2
    genre1 should have(
      'label ("A1 Content - Z1 Content")
    )
    genre1.concepts.head shouldBe a[Concept[_]]
    genre1.concepts.head should have(
      'label ("A1 Content"),
      labelDerivedConceptId("a1 content")
    )
    genre1.concepts(1) shouldBe a[Place[_]]
    genre1.concepts(1) should have(
      'label ("Z1 Content"),
      sourceIdentifier(
        value = "z1 content",
        identifierType = IdentifierType.LabelDerived,
        ontologyType = "Place")
    )
    genre2.concepts.length shouldBe 2
    genre2 should have(
      'label ("A2 Content - V2 Content")
    )
    genre2.concepts.head shouldBe a[Concept[_]]
    genre2.concepts.head should have(
      'label ("A2 Content"),
      labelDerivedConceptId("a2 content")
    )
    genre2.concepts(1) shouldBe a[Concept[_]]
    genre2.concepts(1) should have(
      'label ("V2 Content"),
      labelDerivedConceptId("v2 content")
    )
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

    val List(genre) = SierraGenres(bibData)

    genre should have(
      'label ("Printed books")
    )

    val List(concept) = genre.concepts
    concept shouldBe a[Concept[_]]
    concept should have(
      'label ("Printed books"),
      labelDerivedConceptId("printed books")
    )
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
}
