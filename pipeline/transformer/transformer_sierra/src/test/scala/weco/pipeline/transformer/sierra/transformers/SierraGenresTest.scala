package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{Concept, Genre, Period, Place}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraGenresTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("returns zero genres if there are none") {
    val bibData = createSierraBibDataWith(varFields = List())
    SierraGenres(bibData) shouldBe List()
  }

  it("returns genres for tag 655 with only subfield a") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content",
          concepts = List(Concept(label = "A Content"))))

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
    val expectedGenres =
      List(
        Genre(
          label = "A Content - V Content",
          concepts = List(
            Concept(label = "A Content"),
            Concept(label = "V Content")
          )
        )
      )

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

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it(
    "subfield a is always first concept when returning subjects for tag 655 with subfields a, v") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content - V Content",
          concepts = List(
            Concept(label = "A Content"),
            Concept(label = "V Content")
          )
        )
      )

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

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it("returns genres for tag 655 subfields a, v, and x") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content - X Content - V Content",
          concepts = List(
            Concept(label = "A Content"),
            Concept(label = "X Content"),
            Concept(label = "V Content")
          )
        ))

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

    SierraGenres(bibData) shouldBe expectedGenres
  }

  it("returns subjects for tag 655 with subfields a, y") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content - Y Content",
          concepts = List(
            Concept(label = "A Content"),
            Period(label = "Y Content")
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
}
