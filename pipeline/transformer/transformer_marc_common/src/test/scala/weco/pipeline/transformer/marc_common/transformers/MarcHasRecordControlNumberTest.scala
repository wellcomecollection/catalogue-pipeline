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

    it("finds an identifier with a LoC URL prefix") {
      val field =
        create655FieldWith(
          indicator2 = "0",
          identifierValue = "http://idlocgov/authorities/subjects/sh92000896"
        )

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

    it("finds an identifier with a NLM URL prefix") {
      val field =
        create655FieldWith(
          indicator2 = "2",
          identifierValue = "https://id.nlm.nih.gov/mesh/D049671"
        )

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.MESH,
        value = "D049671",
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


    it("strips (DNLM) prefix") {
      val field =
        create655FieldWith(
          indicator2 = "2",
          identifierValue = "(DNLM)D049671"
        )

      val expectedSourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType.MESH,
        value = "D049671",
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

    it("rejects an invalid LoC identifier without throwing") {
      forAll(
        Table(
          "identifier",
          // Occasionally, source data contains a MeSH id squatting erroneously
          // in a field with indicator2=0
          "D000934",
          // We don't use Children's Subject Headings
          "sj97002429",
          // Sometimes, there are odd typos
          "shsh85100861",
          // A URI form we don't recognise
          "http://id.loc.gov/authorities/genreForms/gf2014026110"
        )
      ) {
        identifier =>
          val field =
            create655FieldWith(indicator2 = "0", identifierValue = identifier)
          MarcHasRecordControlNumber.apply(
            field = field,
            ontologyType = ontologyType
          ) shouldBe IdState.Unidentifiable
      }
    }
  }

  describe("an identifier written as a URI") {
    it("strips the URI prefix, over http or https") {
      forAll(
        Table(
          ("indicator2", "identifier", "identifierType", "value"),
          (
            "0",
            "http://id.loc.gov/authorities/names/n90650979",
            IdentifierType.LCNames,
            "n90650979"
          ),
          (
            "0",
            "https://id.loc.gov/authorities/names/no2008087861",
            IdentifierType.LCNames,
            "no2008087861"
          ),
          (
            "0",
            "http://id.loc.gov/authorities/subjects/sh85062285",
            IdentifierType.LCSubjects,
            "sh85062285"
          ),
          (
            "0",
            "https://id.loc.gov/authorities/subjects/sh85062285",
            IdentifierType.LCSubjects,
            "sh85062285"
          ),
          (
            "2",
            "http://id.nlm.nih.gov/mesh/D004364",
            IdentifierType.MESH,
            "D004364"
          ),
          (
            "2",
            "https://id.nlm.nih.gov/mesh/D004364",
            IdentifierType.MESH,
            "D004364"
          )
        )
      ) {
        (indicator2, identifier, identifierType, value) =>
          val field = create655FieldWith(
            indicator2 = indicator2,
            identifierValue = identifier
          )

          MarcHasRecordControlNumber
            .apply(field = field, ontologyType = ontologyType)
            .allSourceIdentifiers
            .loneElement shouldBe SourceIdentifier(
            identifierType = identifierType,
            value = value,
            ontologyType = ontologyType
          )
      }
    }

    it("treats a URI and a bare id for the same authority as one identifier") {
      val field = MarcField(
        marcTag = "610",
        indicator2 = "0",
        subfields = Seq(
          MarcSubfield("0", "n  86810287"),
          MarcSubfield("0", "http://id.loc.gov/authorities/names/n86810287")
        )
      )

      MarcHasRecordControlNumber
        .apply(field = field, ontologyType = "Organisation")
        .allSourceIdentifiers
        .loneElement shouldBe SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        value = "n86810287",
        ontologyType = "Organisation"
      )
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
