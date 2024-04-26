package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.marc_common.models.MarcField

class MarcConceptIdentifierTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  val ontologyType = "Concept"
  describe("a field with indicator2 set to 0") {
    it("finds an LCSH identifier") {
      val field = create655FieldWith(indicator2 = "0")

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCSubjects,
        value = "sh2009124405",
        ontologyType = ontologyType
      )

      val actualSourceIdentifier = MarcConceptIdentifier
        .apply(
          field = field,
          identifierSubfieldContent = "sh2009124405",
          ontologyType = ontologyType
        )
        .get

      actualSourceIdentifier shouldBe expectedSourceIdentifier
    }

    it("finds an LC-Names identifier") {
      val field = create655FieldWith(indicator2 = "0")

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        value = "n84165387",
        ontologyType = ontologyType
      )

      val actualSourceIdentifier = MarcConceptIdentifier
        .apply(
          field = field,
          identifierSubfieldContent = "n84165387",
          ontologyType = ontologyType
        )
        .get

      actualSourceIdentifier shouldBe expectedSourceIdentifier
    }

    it("throws an exception on an invalid LoC identifier") {
      forAll(
        Table(
          "identifier",
          // Occasionally, source data contains a MeSH id squatting erroneously
          // in a field with indicator2=0
          "D000934",
          // We don't use Children's Subject Headings
          "sj97002429",
          // Sometimes, there are odd typos
          "shsh85100861"
        )
      ) {
        identifier =>
          val field = create655FieldWith(indicator2 = "0")
          assertThrows[IllegalArgumentException] {
            MarcConceptIdentifier
              .apply(
                field = field,
                identifierSubfieldContent = identifier,
                ontologyType = ontologyType
              )
              .get
          }
      }
    }
  }

  it("finds a MESH identifier") {
    val field = create655FieldWith(indicator2 = "2")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = ontologyType
    )

    val actualSourceIdentifier = MarcConceptIdentifier
      .apply(
        field = field,
        identifierSubfieldContent = "mesh/456",
        ontologyType = ontologyType
      )
      .get

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  it("finds a no-ID identifier if indicator 2 = 4") {
    val field = create655FieldWith(indicator2 = "4")

    MarcConceptIdentifier.apply(
      field = field,
      identifierSubfieldContent = "noid/000",
      ontologyType = ontologyType
    ) shouldBe None
  }

  it("returns None if indicator 2 is empty") {
    val field = create655FieldWith(indicator2 = "")

    MarcConceptIdentifier.apply(
      field = field,
      identifierSubfieldContent = "lcsh/789",
      ontologyType = ontologyType
    ) shouldBe None
  }

  it("returns None if it sees an unrecognised identifier scheme") {
    val field = create655FieldWith(indicator2 = "8")

    MarcConceptIdentifier.apply(
      field = field,
      identifierSubfieldContent = "u/xxx",
      ontologyType = ontologyType
    ) shouldBe None
  }

  it("passes through the ontology type") {
    val field = create655FieldWith(indicator2 = "2")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = "Item"
    )

    val actualSourceIdentifier = MarcConceptIdentifier
      .apply(
        field = field,
        identifierSubfieldContent = "mesh/456",
        ontologyType = "Item"
      )
      .get

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  private def create655FieldWith(indicator2: String): MarcField = {
    MarcField(
      marcTag = "655",
      indicator2 = indicator2
    )
  }
}
