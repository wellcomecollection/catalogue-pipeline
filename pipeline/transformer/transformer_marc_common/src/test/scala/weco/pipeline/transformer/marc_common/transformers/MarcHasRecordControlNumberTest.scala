package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcHasRecordControlNumberTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks
    with LoneElement {
  val ontologyType = "Concept"
  describe("a field with indicator2 set to 0") {
    it("finds an LCSH identifier") {
      val field =
        create655FieldWith(indicator2 = "0", identifierValue = "sh2009124405")

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCSubjects,
        value = "sh2009124405",
        ontologyType = ontologyType
      )

      val actualSourceIdentifier = MarcHasRecordControlNumber
        .apply(
          field = field,
          ontologyType = ontologyType
        )
        .allSourceIdentifiers
        .loneElement

      actualSourceIdentifier shouldBe expectedSourceIdentifier
    }

    it("finds an LC-Names identifier") {
      val field =
        create655FieldWith(indicator2 = "0", identifierValue = "n84165387")

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        value = "n84165387",
        ontologyType = ontologyType
      )

      val actualSourceIdentifier = MarcHasRecordControlNumber
        .apply(
          field = field,
          ontologyType = ontologyType
        )
        .allSourceIdentifiers
        .loneElement

      actualSourceIdentifier shouldBe expectedSourceIdentifier
    }

    it("finds an identifier with a URL prefix") {
      val field =
        create655FieldWith(indicator2 = "0", identifierValue = "http://idlocgov/authorities/subjects/sh92000896")

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.LCSubjects,
        value = "sh92000896",
        ontologyType = ontologyType
      )

      val actualSourceIdentifier = MarcHasRecordControlNumber
        .apply(
          field = field,
          ontologyType = ontologyType
        )
        .allSourceIdentifiers
        .loneElement

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
          val field =
            create655FieldWith(indicator2 = "0", identifierValue = identifier)
          assertThrows[IllegalArgumentException] {
            MarcHasRecordControlNumber
              .apply(
                field = field,
                ontologyType = ontologyType
              )
              .allSourceIdentifiers
              .loneElement

          }
      }
    }
  }

  it("finds a MESH identifier") {
    val field =
      create655FieldWith(indicator2 = "2", identifierValue = "mesh/456")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = ontologyType
    )

    val actualSourceIdentifier = MarcHasRecordControlNumber
      .apply(
        field = field,
        ontologyType = ontologyType
      )
      .allSourceIdentifiers
      .loneElement

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  it("finds a no-ID identifier if indicator 2 = 4") {
    val field =
      create655FieldWith(indicator2 = "4", identifierValue = "noid/000")

    MarcHasRecordControlNumber.apply(
      field = field,
      ontologyType = ontologyType
    ) shouldBe IdState.Unidentifiable
  }

  it("returns None if indicator 2 is empty") {
    val field = create655FieldWith(indicator2 = "", "lcsh/789")

    MarcHasRecordControlNumber.apply(
      field = field,
      ontologyType = ontologyType
    ) shouldBe IdState.Unidentifiable
  }

  it("returns None if it sees an unrecognised identifier scheme") {
    val field = create655FieldWith(indicator2 = "8", "u/xxx")

    MarcHasRecordControlNumber.apply(
      field = field,
      ontologyType = ontologyType
    ) shouldBe IdState.Unidentifiable
  }

  it("passes through the ontology type") {
    val field = create655FieldWith(indicator2 = "2", "mesh/456")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = "Item"
    )

    val actualSourceIdentifier = MarcHasRecordControlNumber
      .apply(
        field = field,
        ontologyType = "Item"
      )
      .allSourceIdentifiers
      .loneElement

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  private def create655FieldWith(
    indicator2: String,
    identifierValue: String
  ): MarcField = {
    MarcField(
      marcTag = "655",
      indicator2 = indicator2,
      subfields = Seq(MarcSubfield("0", identifierValue))
    )
  }
}
