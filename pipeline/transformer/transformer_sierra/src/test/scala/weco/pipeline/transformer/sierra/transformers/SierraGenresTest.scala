package weco.pipeline.transformer.sierra.transformers

import org.scalatest.{Assertions, Inside}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.LoneElement._
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  Concept,
  Genre,
  GenreConcept,
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
    with Assertions
    with Inside
    with Matchers
    with ConceptMatchers
    with HasIdMatchers
    with MarcGenerators
    with SierraDataGenerators {
  override def suiteName = "Genres"
  describe("extracting the right number of genres from a Sierra document") {

    it("returns zero genres if there are none") {
      val bibData = createSierraBibDataWith(varFields = List())
      SierraGenres(bibData) shouldBe Nil
    }

    it("extracts a genre for each different MARC 655 field in the document") {
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

      inside(SierraGenres(bibData)) {
        case Seq(genre1, genre2) =>
          genre1.label shouldBe "A1 Content - Z1 Content"
          genre2.label shouldBe "A2 Content - V2 Content"
      }
    }

    it("deduplicates transformed genres") {
      info(
        """
          Although the MARC data for any two genres may differ, 
          once they have been transformed into a Genre, the
          differences can vanish.
          
          The genres list should be deduplicated based on the 
          actual content of the list.
        """
      )
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
          )
        )
      )

      val genres = SierraGenres(bibData)
      List("Electronic journals", "Periodical", "Periodicals").zip(genres).map {
        case (genreName, genre) =>
          genre.label shouldBe genreName
      }
    }
  }

  describe("extracting simple genres from MARC 655 fields") {

    it("returns a genre with a single concept when ǂa is the only subfield") {

      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "655",
            subfields = List(Subfield(tag = "a", content = "A Content"))
          )
        )
      )

      val actualGenre = SierraGenres(bibData).loneElement
      actualGenre shouldBe a[Genre[_]]
      actualGenre should have(
        'label("A Content")
      )

      actualGenre.concepts.loneElement shouldBe a[GenreConcept[_]]
      actualGenre.concepts.loneElement should have(
        sourceIdentifier(
          identifierType = IdentifierType.LabelDerived,
          ontologyType = "Genre",
          value = "a content"
        )
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

      inside(SierraGenres(bibData)) {
        case Seq(genre) =>
          genre.label shouldBe "Printed books"
          inside(genre.concepts) {
            case Seq(concept: GenreConcept[_]) =>
              concept should have(
                'label("Printed books"),
                labelDerivedGenreId("printed books")
              )

          }
      }
    }
  }

  describe("extracting compound genres from MARC 655 fields") {
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
      val genre = SierraGenres(bibData).loneElement

      genre should have(
        'label("A Content - V Content")
      )

      val List(conceptA, conceptV) = genre.concepts
      conceptA shouldBe a[GenreConcept[_]]
      conceptA should have(
        'label("A Content"),
        labelDerivedGenreId("a content")
      )
      conceptV shouldBe a[Concept[_]]
      conceptV should have(
        'label("V Content"),
        labelDerivedConceptId("v content")
      )
    }

    it(
      "subfield a is always first concept when returning subjects for tag 655 with subfields a, v"
    ) {

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

      val genre = SierraGenres(bibData).loneElement

      genre should have(
        'label("A Content - V Content")
      )

      val List(conceptA, conceptV) = genre.concepts
      conceptA shouldBe a[GenreConcept[_]]
      conceptA should have(
        'label("A Content"),
        labelDerivedGenreId("a content")
      )
      conceptV shouldBe a[Concept[_]]
      conceptV should have(
        'label("V Content"),
        labelDerivedConceptId("v content")
      )
    }

    it("returns a genre for a 655 field with subfields a, v, and x") {
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
      inside(genre) {
        case Genre(label, concepts) =>
          label shouldBe "A Content - X Content - V Content"
          inside(concepts) {
            case Seq(
                  conceptA: GenreConcept[_],
                  conceptX: Concept[_],
                  conceptV: Concept[_]
                ) =>
              conceptA should have(
                'label("A Content"),
                labelDerivedGenreId("a content")
              )
              conceptX should have(
                'label("X Content"),
                labelDerivedConceptId("x content")
              )
              conceptV should have(
                'label("V Content"),
                labelDerivedConceptId("v content")
              )
          }

      }

      genre should have(
        'label("A Content - X Content - V Content")
      )

      val List(conceptA, conceptX, conceptV) = genre.concepts
      conceptA shouldBe a[GenreConcept[_]]

      conceptA should have(
        'label("A Content"),
        labelDerivedGenreId("a content")
      )
      conceptX shouldBe a[Concept[_]]
      conceptX should have(
        'label("X Content"),
        labelDerivedConceptId("x content")
      )
      conceptV shouldBe a[Concept[_]]
      conceptV should have(
        'label("V Content"),
        labelDerivedConceptId("v content")
      )
    }
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
      'label("A Content - MDCCLXXXVII. [1787]")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a[GenreConcept[_]]
    conceptA should have(
      'label("A Content"),
      labelDerivedGenreId("a content")
    )
    conceptV shouldBe a[Period[_]]
    conceptV should have(
      'label("MDCCLXXXVII. [1787]"),
      labelDerivedPeriodId("1787"),
      'range(
        Some(
          InstantRange(
            LocalDate of (1787, 1, 1),
            LocalDate of (1787, 12, 31),
            "MDCCLXXXVII. [1787]"
          )
        )
      )
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
      'label("A Content - Z Content")
    )

    val List(conceptA, conceptV) = genre.concepts
    conceptA shouldBe a[GenreConcept[_]]
    conceptA should have(
      'label("A Content"),
      labelDerivedGenreId("a content")
    )
    conceptV shouldBe a[Place[_]]
    conceptV should have(
      'label("Z Content"),
      labelDerivedPlaceId("z content")
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
            Subfield(tag = "0", content = "sh85060628")
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
        value = "sh85060628",
        ontologyType = "Genre"
      ),
      SourceIdentifier(
        identifierType = IdentifierType.MESH,
        value = "mesh/456",
        ontologyType = "Genre"
      )
    )

    val actualSourceIdentifiers = SierraGenres(bibData)
      .map { _.concepts.head.id }
      .map {
        case IdState.Identifiable(sourceIdentifier, _, _) =>
          sourceIdentifier
        case other => fail(other.toString)
      }

    expectedSourceIdentifiers shouldBe actualSourceIdentifiers
  }
}
