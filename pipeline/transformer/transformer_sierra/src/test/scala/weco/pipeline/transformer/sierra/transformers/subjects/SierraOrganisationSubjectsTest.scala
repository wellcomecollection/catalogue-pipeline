package weco.pipeline.transformer.sierra.transformers.subjects

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.sierra.exceptions.CataloguingException
import weco.pipeline.transformer.sierra.transformers.matchers.{
  ConceptMatchers,
  HasIdMatchers
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

// TODO: There is a bunch of commonality between the different SubjectsTests that could be DRYed out

class SierraOrganisationSubjectsTest
    extends AnyFunSpec
    with Matchers
    with HasIdMatchers
    with ConceptMatchers
    with SierraDataGenerators {
  it("returns an empty list if there are no instances of MARC tag 610") {
    val bibData = createSierraBibDataWith(varFields = Nil)
    getOrganisationSubjects(bibData) shouldBe Nil
  }

  describe("label") {
    it("uses subfields a, b, c, d and e as the subject label") {
      val bibData = create610bibDataWith(
        subfields = List(
          Subfield(tag = "a", content = "United States."),
          Subfield(tag = "b", content = "Supreme Court,"),
          Subfield(tag = "c", content = "Washington, DC."),
          Subfield(tag = "d", content = "September 29, 2005,"),
          Subfield(tag = "e", content = "pictured.")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      subjects should have size 1

      subjects.head.label shouldBe "United States. Supreme Court, Washington, DC. September 29, 2005, pictured"
    }

    it("uses repeated subfields for the label if necessary") {
      // This is based on an example from the MARC spec
      val bibData = create610bibDataWith(
        subfields = List(
          Subfield(tag = "a", content = "United States."),
          Subfield(tag = "b", content = "Army."),
          Subfield(tag = "b", content = "Cavalry, 7th."),
          Subfield(tag = "b", content = "Company E,"),
          Subfield(tag = "e", content = "depicted.")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      subjects should have size 1

      subjects.head.label shouldBe "United States. Army. Cavalry, 7th. Company E, depicted"
    }
  }

  describe("concepts") {
    it("creates an Organisation as the concept") {
      val bibData = create610bibDataWith(
        subfields = List(
          Subfield(tag = "a", content = "Wellcome Trust."),
        )
      )

      val List(subject) = getOrganisationSubjects(bibData)

      val List(concept) = subject.concepts
      concept should have(
        'label ("Wellcome Trust"),
        sourceIdentifier(
          value = "Wellcome Trust",
          ontologyType = "Organisation",
          identifierType = IdentifierType.LabelDerived)
      )
    }

    it("uses subfields a and b for the Organisation label") {
      val bibData = create610bibDataWith(
        subfields = List(
          Subfield(tag = "a", content = "Wellcome Trust."),
          Subfield(tag = "b", content = "Facilities,"),
          Subfield(tag = "b", content = "Health & Safety"),
          Subfield(tag = "c", content = "27 September 2018")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.concepts
      concepts.head.label shouldBe "Wellcome Trust. Facilities, Health & Safety"
    }

    it("creates an Identifiable Organisation using subfield 0, if present") {
      val lcNamesCode = "n81290903210"
      val bibData = create610bibDataWith(
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "ACME Corp"),
          Subfield(tag = "0", content = lcNamesCode)
        )
      )

      val subjects = getOrganisationSubjects(bibData)

      val subject = subjects.head

      subject.id shouldBe IdState.Identifiable(
        SourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Subject",
          value = lcNamesCode
        )
      )
    }

    it("assumes LCNames if no indicator2 is given") {
      val lcNamesCode = "n81290903210"
      val bibData = create610bibDataWith(
        indicator2 = "",
        subfields = List(
          Subfield(tag = "a", content = "ACME Corp"),
          Subfield(tag = "0", content = lcNamesCode)
        )
      )

      val List(subject) = getOrganisationSubjects(bibData)

      subject should have (
        sourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Subject",
          value = lcNamesCode
        )
      )
    }

    it(
      "creates an Identifiable Organisation if subfield 0 has multiple but unambiguous values") {
      val bibData = create610bibDataWith(
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "ACME Corp"),
          Subfield(tag = "0", content = "  n1234"),
          Subfield(tag = "0", content = "n1234"),
        )
      )

      val subjects = getOrganisationSubjects(bibData)

      val subject = subjects.head

      subject.id shouldBe IdState.Identifiable(
        SourceIdentifier(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Subject",
          value = "n1234"
        )
      )
    }

    it("skips adding an identifier if subfield 0 is ambiguous") {
      val bibData = create610bibDataWith(
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "ACME Corp"),
          Subfield(tag = "0", content = "n12345"),
          Subfield(tag = "0", content = "n67890")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.concepts
      val unmintedOrganisation = concepts.head
      unmintedOrganisation.id shouldBe IdState.Unidentifiable
    }

    it("skips adding an identifier if a specified 2nd indicator is not '0'") {
      val bibData = create610bibDataWith(
        indicator2 = "2",
        subfields = List(
          Subfield(tag = "a", content = "ACME Corp"),
          Subfield(tag = "0", content = "n12345")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.concepts
      val unmintedOrganisation = concepts.head
      unmintedOrganisation.id shouldBe IdState.Unidentifiable
    }
  }

  describe("missing label") {

    // This is probably a cataloguing error; in this case we should just
    // throw and have somebody inspect the record manually.  It's not at all
    // clear what we should do, as we don't have enough to populate the
    // Organisation label.
    it("errors if there's nothing in subfield a or b") {
      val varField = createMarc610VarField(subfields = List())
      val bibData = createSierraBibDataWith(varFields = List(varField))

      val bibId = createSierraBibNumber

      val caught = intercept[CataloguingException] {
        getOrganisationSubjects(bibId = bibId, bibData = bibData)
      }

      caught.getMessage should startWith("Problem in the Sierra data")
      caught.getMessage should include(bibId.withoutCheckDigit)
      caught.getMessage should include(
        "Not enough information to build a label")
    }
  }

  it("gets multiple instances of the MARC 610 field") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createMarc610VarField(
          subfields = List(
            Subfield(tag = "a", content = "ACME Corp.")
          )
        ),
        createMarc610VarField(
          subfields = List(
            Subfield(tag = "a", content = "BBC.")
          )
        ),
        createMarc610VarField(
          subfields = List(
            Subfield(tag = "a", content = "Charlie's Chocolate Factory.")
          )
        )
      )
    )

    val subjects = getOrganisationSubjects(bibData)
    subjects should have size 3
  }

  private def create610bibDataWith(subfields: List[Subfield],
                                   indicator2: String = ""): SierraBibData =
    createSierraBibDataWith(
      varFields = List(
        createMarc610VarField(subfields = subfields, indicator2 = indicator2)
      )
    )

  private def createMarc610VarField(subfields: List[Subfield],
                                    indicator2: String = ""): VarField =
    VarField(
      marcTag = Some("610"),
      indicator1 = Some(""),
      indicator2 = Some(indicator2),
      subfields = subfields
    )

  private def getOrganisationSubjects(bibData: SierraBibData,
                                      bibId: SierraBibNumber =
                                        createSierraBibNumber) =
    SierraOrganisationSubjects(bibId, bibData)
}
