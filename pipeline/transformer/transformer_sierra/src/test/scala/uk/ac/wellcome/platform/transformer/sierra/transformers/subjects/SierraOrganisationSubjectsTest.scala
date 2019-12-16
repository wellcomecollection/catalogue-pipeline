package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.CataloguingException
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  VarField
}

class SierraOrganisationSubjectsTest
    extends FunSpec
    with Matchers
    with SierraDataGenerators {
  it("returns an empty list if there are no instances of MARC tag 610") {
    val bibData = createSierraBibDataWith(varFields = Nil)
    getOrganisationSubjects(bibData) shouldBe Nil
  }

  describe("label") {
    it("uses subfields a, b, c, d and e as the label") {
      val bibData = create610bibDataWith(
        subfields = List(
          MarcSubfield(tag = "a", content = "United States."),
          MarcSubfield(tag = "b", content = "Supreme Court,"),
          MarcSubfield(tag = "c", content = "Washington, DC."),
          MarcSubfield(tag = "d", content = "September 29, 2005,"),
          MarcSubfield(tag = "e", content = "pictured.")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      subjects should have size 1

      subjects.head.agent.label shouldBe "United States. Supreme Court, Washington, DC. September 29, 2005, pictured."
    }

    it("uses repeated subfields for the label if necessary") {
      // This is based on an example from the MARC spec
      val bibData = create610bibDataWith(
        subfields = List(
          MarcSubfield(tag = "a", content = "United States."),
          MarcSubfield(tag = "b", content = "Army."),
          MarcSubfield(tag = "b", content = "Cavalry, 7th."),
          MarcSubfield(tag = "b", content = "Company E,"),
          MarcSubfield(tag = "e", content = "depicted.")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      subjects should have size 1

      subjects.head.agent.label shouldBe "United States. Army. Cavalry, 7th. Company E, depicted."
    }
  }

  describe("concepts") {
    it("creates an Organisation as the concept") {
      val bibData = create610bibDataWith(
        subfields = List(
          MarcSubfield(tag = "a", content = "Wellcome Trust."),
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.agent.concepts
      concepts should have size 1

      val maybeDisplayableOrganisation = concepts.head
      maybeDisplayableOrganisation shouldBe a[Unidentifiable[_]]
    }

    it("uses subfields a and b for the Organisation label") {
      val bibData = create610bibDataWith(
        subfields = List(
          MarcSubfield(tag = "a", content = "Wellcome Trust."),
          MarcSubfield(tag = "b", content = "Facilities,"),
          MarcSubfield(tag = "b", content = "Health & Safety"),
          MarcSubfield(tag = "c", content = "27 September 2018")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.agent.concepts
      val organisation = concepts.head.agent
      organisation.label shouldBe "Wellcome Trust. Facilities, Health & Safety"
    }

    it("creates an Identifiable Organisation if subfield 0 is present") {
      val lcNamesCode = "n81290903210"
      val bibData = create610bibDataWith(
        indicator2 = "0",
        subfields = List(
          MarcSubfield(tag = "a", content = "ACME Corp"),
          MarcSubfield(tag = "0", content = lcNamesCode)
        )
      )

      val subjects = getOrganisationSubjects(bibData)

      val subject = subjects.head
      val identifiableSubject = subject
        .asInstanceOf[Identifiable[Subject[Unidentifiable[Organisation]]]]

      identifiableSubject.sourceIdentifier shouldBe SourceIdentifier(
        identifierType = IdentifierType("lc-names"),
        ontologyType = "Subject",
        value = lcNamesCode
      )
    }

    it(
      "creates an Identifiable Organisation if subfield 0 has multiple but unambiguous values") {
      val bibData = create610bibDataWith(
        indicator2 = "0",
        subfields = List(
          MarcSubfield(tag = "a", content = "ACME Corp"),
          MarcSubfield(tag = "0", content = "  n1234"),
          MarcSubfield(tag = "0", content = "n1234"),
        )
      )

      val subjects = getOrganisationSubjects(bibData)

      val subject = subjects.head
      val identifiableSubject = subject
        .asInstanceOf[Identifiable[Subject[Unidentifiable[Organisation]]]]

      identifiableSubject.sourceIdentifier shouldBe SourceIdentifier(
        identifierType = IdentifierType("lc-names"),
        ontologyType = "Subject",
        value = "n1234"
      )
    }

    it("skips adding an identifier if subfield 0 is ambiguous") {
      val bibData = create610bibDataWith(
        indicator2 = "0",
        subfields = List(
          MarcSubfield(tag = "a", content = "ACME Corp"),
          MarcSubfield(tag = "0", content = "n12345"),
          MarcSubfield(tag = "0", content = "n67890")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.agent.concepts
      val maybeDisplayableOrganisation = concepts.head
      maybeDisplayableOrganisation shouldBe a[Unidentifiable[_]]
    }

    it("skips adding an identifier if the 2nd indicator is not '0'") {
      val bibData = create610bibDataWith(
        indicator2 = "2",
        subfields = List(
          MarcSubfield(tag = "a", content = "ACME Corp"),
          MarcSubfield(tag = "0", content = "n12345")
        )
      )

      val subjects = getOrganisationSubjects(bibData)
      val concepts = subjects.head.agent.concepts
      val maybeDisplayableOrganisation = concepts.head
      maybeDisplayableOrganisation shouldBe a[Unidentifiable[_]]
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
            MarcSubfield(tag = "a", content = "ACME Corp.")
          )
        ),
        createMarc610VarField(
          subfields = List(
            MarcSubfield(tag = "a", content = "BBC.")
          )
        ),
        createMarc610VarField(
          subfields = List(
            MarcSubfield(tag = "a", content = "Charlie's Chocolate Factory.")
          )
        )
      )
    )

    val subjects = getOrganisationSubjects(bibData)
    subjects should have size 3
  }

  private def create610bibDataWith(subfields: List[MarcSubfield],
                                   indicator2: String = ""): SierraBibData =
    createSierraBibDataWith(
      varFields = List(
        createMarc610VarField(subfields = subfields, indicator2 = indicator2)
      )
    )

  private def createMarc610VarField(subfields: List[MarcSubfield],
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
