package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield

class SierraIdentifiersTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("passes through the main identifier from the bib record") {
    val bibId = createSierraBibNumber
    val expectedIdentifiers = List(
      SourceIdentifier(
        identifierType = IdentifierType("sierra-identifier"),
        ontologyType = "Work",
        value = bibId.withoutCheckDigit
      )
    )
    SierraIdentifiers(bibId, createSierraBibData) shouldBe expectedIdentifiers
  }

  describe("finds ISBN identifiers from MARC 020 ǂa") {
    it("a single identifier") {
      val isbn = "1785783033"
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "020",
            subfields = List(
              MarcSubfield(tag = "a", content = isbn)
            )
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType("isbn"),
          ontologyType = "Work",
          value = isbn
        ))
    }

    it("multiple identifiers") {
      val isbn10 = "1473647649"
      val isbn13 = "978-1473647640"
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "020",
            subfields = List(
              MarcSubfield(tag = "a", content = isbn10)
            )
          ),
          createVarFieldWith(
            marcTag = "020",
            subfields = List(
              MarcSubfield(tag = "a", content = isbn13)
            )
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType("isbn"),
          ontologyType = "Work",
          value = isbn10
        ))
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType("isbn"),
          ontologyType = "Work",
          value = isbn13
        ))
    }

    it("deduplicates identifiers") {
      val isbn = "1785783033"
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "020",
            subfields = List(
              MarcSubfield(tag = "a", content = isbn)
            )
          ),
          createVarFieldWith(
            marcTag = "020",
            subfields = List(
              MarcSubfield(tag = "a", content = isbn)
            )
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val isbnIdentifiers = otherIdentifiers.filter { _.identifierType.id == "isbn" }
      isbnIdentifiers should have size 1
    }
  }

  describe("finds ISSN identifiers from MARC 022 ǂa") {
    it("a single identifier") {
      val issn = "0305-3342"
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "022",
            subfields = List(
              MarcSubfield(tag = "a", content = issn)
            )
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType("issn"),
          ontologyType = "Work",
          value = issn
        ))
    }

    it("multiple identifiers") {
      val issn1 = "0305-3342"
      val issn2 = "0019-2422"
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "022",
            subfields = List(
              MarcSubfield(tag = "a", content = issn1)
            )
          ),
          createVarFieldWith(
            marcTag = "022",
            subfields = List(
              MarcSubfield(tag = "a", content = issn2)
            )
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType("issn"),
          ontologyType = "Work",
          value = issn1
        ))
      otherIdentifiers should contain(
        SourceIdentifier(
          identifierType = IdentifierType("issn"),
          ontologyType = "Work",
          value = issn2
        ))
    }

    it("deduplicates identifiers") {
      val issn = "0305-3342"
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "022",
            subfields = List(
              MarcSubfield(tag = "a", content = issn)
            )
          ),
          createVarFieldWith(
            marcTag = "022",
            subfields = List(
              MarcSubfield(tag = "a", content = issn)
            )
          )
        )
      )
      val otherIdentifiers = SierraIdentifiers(createSierraBibNumber, bibData)

      val issnIdentifiers = otherIdentifiers.filter { _.identifierType.id == "issn" }
      issnIdentifiers should have size 1
    }
  }
}
