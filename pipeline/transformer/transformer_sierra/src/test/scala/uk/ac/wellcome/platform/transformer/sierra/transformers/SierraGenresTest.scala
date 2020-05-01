package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraGenresTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("returns zero genres if there are none") {
    val bibData = createSierraBibDataWith(varFields = List())
    assertExtractsGenres(bibData, List())
  }

  it("returns genres for tag 655 with only subfield a") {
    val expectedGenres =
      List(
        Genre(
          label = "A Content",
          concepts = List(Concept(label = "A Content"))))

    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "655",
          subfields = List(MarcSubfield(tag = "a", content = "A Content"))
        )
      )
    )

    assertExtractsGenres(bibData, expectedGenres)
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
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "a", content = "A Content"),
            MarcSubfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    assertExtractsGenres(bibData, expectedGenres)
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
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "v", content = "V Content"),
            MarcSubfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    assertExtractsGenres(bibData, expectedGenres)
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
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "a", content = "A Content"),
            MarcSubfield(tag = "x", content = "X Content"),
            MarcSubfield(tag = "v", content = "V Content")
          )
        )
      )
    )

    assertExtractsGenres(bibData, expectedGenres)
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
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "y", content = "Y Content"),
            MarcSubfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    assertExtractsGenres(bibData, expectedGenres)
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
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "z", content = "Z Content"),
            MarcSubfield(tag = "a", content = "A Content")
          )
        )
      )
    )

    assertExtractsGenres(bibData, expectedGenres)
  }

  it("returns subjects for multiple 655 tags with different subfields") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "a", content = "A1 Content"),
            MarcSubfield(tag = "z", content = "Z1 Content")
          )
        ),
        createVarFieldWith(
          marcTag = "655",
          subfields = List(
            MarcSubfield(tag = "a", content = "A2 Content"),
            MarcSubfield(tag = "v", content = "V2 Content")
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
    assertExtractsGenres(bibData, expectedSubjects)
  }

  it(s"gets identifiers from subfield $$0") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "655",
          // LCSH heading
          indicator2 = "0",
          subfields = List(
            MarcSubfield(tag = "a", content = "absence"),
            MarcSubfield(tag = "0", content = "lcsh/123")
          )
        ),
        createVarFieldWith(
          marcTag = "655",
          // MESH heading
          indicator2 = "2",
          subfields = List(
            MarcSubfield(tag = "a", content = "abolition"),
            MarcSubfield(tag = "0", content = "mesh/456")
          )
        )
      )
    )

    // TODO Get rid of the check method??

    val expectedSourceIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType("lc-subjects"),
        value = "lcsh/123",
        ontologyType = "Concept"
      ),
      SourceIdentifier(
        identifierType = IdentifierType("nlm-mesh"),
        value = "mesh/456",
        ontologyType = "Concept"
      )
    )

    val actualSourceIdentifiers = SierraGenres(createSierraBibNumber, bibData)
      .map { _.concepts.head.id }
      .map {
        case Identifiable(sourceIdentifier, _, _) =>
          sourceIdentifier
        case other => assert(false, other)
      }

    expectedSourceIdentifiers shouldBe actualSourceIdentifiers
  }

  private def assertExtractsGenres(bibData: SierraBibData,
                                   expected: List[Genre[Unminted]]) = {
    SierraGenres(createSierraBibNumber, bibData) shouldBe expected
  }
}
