package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.{Subfield, VarField}

class SierraIdentifiersTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with SierraDataGenerators {

  it("passes through the main identifier from the bib record") {
    val bibId = createSierraBibNumber
    val expectedIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType.SierraIdentifier,
        ontologyType = "Work",
        value = bibId.withoutCheckDigit
      )
    )
    SierraIdentifiers(bibId, createSierraBibData) shouldBe expectedIdentifiers
  }

  describe("finds ISBN identifiers from MARC 020 ǂa") {
    // This example is taken from b1754201
    val isbn10 = "159463078X"
    val isbn13 = "9781594630781"

    it("a single identifier") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "020",
            subfields = List(Subfield(tag = "a", content = isbn10))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ISBN,
          ontologyType = "Work",
          value = isbn10
        )
      )
    }

    it("multiple identifiers") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "020",
            subfields = List(Subfield(tag = "a", content = isbn10))
          ),
          VarField(
            marcTag = "020",
            subfields = List(Subfield(tag = "a", content = isbn13))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ISBN,
          ontologyType = "Work",
          value = isbn10
        )
      )
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ISBN,
          ontologyType = "Work",
          value = isbn13
        )
      )
    }

    it("deduplicates identifiers") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "020",
            subfields = List(Subfield(tag = "a", content = isbn10))
          ),
          VarField(
            marcTag = "020",
            subfields = List(Subfield(tag = "a", content = isbn10))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val isbnIdentifiers = otherIdentifiers.filter {
        _.identifierType.id == "isbn"
      }
      isbnIdentifiers should have size 1
    }

    it("strips whitespace") {
      // Based on https://api.wellcomecollection.org/catalogue/v2/works/mhvnscj7?include=identifiers
      val isbn = "978-1479144075"

      val expectedIdentifier = SourceIdentifier(
        identifierType = IdentifierType.ISBN,
        ontologyType = "Work",
        value = isbn
      )

      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "020",
            subfields = List(Subfield(tag = "a", content = s" $isbn"))
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(expectedIdentifier)
    }
  }

  describe("finds ISSN identifiers from MARC 022 ǂa") {
    it("a single identifier") {
      val issn = "0305-3342"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "022",
            subfields = List(Subfield(tag = "a", content = issn))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ISSN,
          ontologyType = "Work",
          value = issn
        )
      )
    }

    it("multiple identifiers") {
      val issn1 = "0305-3342"
      val issn2 = "0019-2422"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "022",
            subfields = List(Subfield(tag = "a", content = issn1))
          ),
          VarField(
            marcTag = "022",
            subfields = List(Subfield(tag = "a", content = issn2))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ISSN,
          ontologyType = "Work",
          value = issn1
        )
      )
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ISSN,
          ontologyType = "Work",
          value = issn2
        )
      )
    }

    it("deduplicates identifiers") {
      val issn = "0305-3342"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "022",
            subfields = List(Subfield(tag = "a", content = issn))
          ),
          VarField(
            marcTag = "022",
            subfields = List(Subfield(tag = "a", content = issn))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val issnIdentifiers = otherIdentifiers.filter {
        _.identifierType.id == "issn"
      }
      issnIdentifiers should have size 1
    }

    it("strips whitespace") {
      // Based on https://api.wellcomecollection.org/catalogue/v2/works/t9wua9ys?include=identifiers
      val issn = "0945-7704"

      val expectedIdentifier = SourceIdentifier(
        identifierType = IdentifierType.ISSN,
        ontologyType = "Work",
        value = issn
      )

      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "022",
            subfields = List(Subfield(tag = "a", content = s"$issn "))
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(expectedIdentifier)
    }
  }

  describe("finds digcodes from MARC 759 ǂa") {
    it("a single identifier") {
      val digcode = "digrcs"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = digcode))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(createDigcodeIdentifier(digcode))
    }

    it("multiple identifiers") {
      // This example is based on b22474262
      val digcode1 = "digrcs"
      val digcode2 = "digukmhl"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = digcode1))
          ),
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = digcode2))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(createDigcodeIdentifier(digcode1))
      otherIdentifiers should contain(createDigcodeIdentifier(digcode2))
    }

    it("deduplicates identifiers") {
      val digcode = "digrcs"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = digcode))
          ),
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = digcode))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val digcodeIdentifiers = otherIdentifiers.filter {
        _.identifierType.id == "wellcome-digcode"
      }
      digcodeIdentifiers should have size 1
    }

    it("skips values in MARC 759 which aren't digcodes") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          // Although this starts with the special string `dig`, the lack
          // of any extra information makes it useless for identifying a
          // digitisation project!
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = "dig"))
          ),
          // digcodes have to start with the special string `dig`
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = "notadigcode"))
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val digcodeIdentifiers = otherIdentifiers.filter {
        _.identifierType.id == "wellcome-digcode"
      }
      digcodeIdentifiers shouldBe empty
    }

    it("only captures the continuous alphabetic string starting `dig`") {
      // This example is based on b29500783
      val marcDigcode = "digmoh(Channel)"
      val parsedDigcode = "digmoh"

      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = marcDigcode))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(createDigcodeIdentifier(parsedDigcode))
    }

    it("deduplicates based on the actual digcode") {
      val digcode = "digmoh"
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "759",
            subfields = List(Subfield(tag = "a", content = digcode))
          ),
          VarField(
            marcTag = "759",
            subfields =
              List(Subfield(tag = "a", content = s"$digcode(Channel)"))
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val digcodeIdentifiers = otherIdentifiers.filter {
        _.identifierType.id == "wellcome-digcode"
      }
      digcodeIdentifiers should have size 1
    }
  }

  it("finds the iconographic number from field 001 on visual collections") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("r")),
      varFields = List(
        VarField(marcTag = Some("001"), content = Some("12345i"))
      )
    )

    val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
    otherIdentifiers should contain(
      SourceIdentifier(
        identifierType = IdentifierType.IconographicNumber,
        value = "12345i",
        ontologyType = "Work"
      )
    )
  }

  describe("ESTC references in MARC 510 ǂc") {
    it("extracts a valid ESTC reference") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "510",
            subfields = List(
              Subfield(tag = "a", content = "ESTC"),
              Subfield(tag = "c", content = "N16242")
            )
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType.ESTC,
          value = "N16242",
          ontologyType = "Work"
        )
      )
    }

    it("skips the identifier if ǂa is not 'ESTC'") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "510",
            subfields = List(
              Subfield(tag = "a", content = "notESTC"),
              Subfield(tag = "c", content = "N16242")
            )
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers.filter {
        _.identifierType == IdentifierType.ESTC
      } shouldBe empty
    }

    it("skips the identifier if ǂc doesn't look like an ESTC reference") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "510",
            subfields = List(
              Subfield(tag = "a", content = "ESTC"),
              Subfield(tag = "c", content = "cf. Teerink-Scouten 500")
            )
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers.filter {
        _.identifierType == IdentifierType.ESTC
      } shouldBe empty
    }

    it("skips the identifier if there are multiple values of subfield ǂc") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "510",
            subfields = List(
              Subfield(tag = "a", content = "ESTC"),
              Subfield(tag = "c", content = "NCBEL,"),
              Subfield(tag = "c", content = "II:1306")
            )
          )
        )
      )

      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers.filter {
        _.identifierType == IdentifierType.ESTC
      } shouldBe empty
    }
  }
}
