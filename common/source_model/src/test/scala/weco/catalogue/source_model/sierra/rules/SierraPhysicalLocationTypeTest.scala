package weco.catalogue.source_model.sierra.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.locations.LocationType
import weco.sierra.generators.SierraIdentifierGenerators

class SierraPhysicalLocationTypeTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks
    with SierraIdentifierGenerators {
  // These test cases are based on location names from every item in the
  // Sierra catalogue, as retrieved at the start of February 2021.

  val id = createSierraBibNumber

  it("maps names to ClosedStores") {
    val testCases = Table(
      "name",
      "Archives & Mss Well.Coll.",
      "At Digitisation",
      "By appointment",
      "Closed stores",
      "Closed stores A&MSS RAMC",
      "Closed stores P.B. Uzbek",
      "Conservation",
      "Early Printed Books /Supp",
      "Iconographic Collection",
      "OBSOLETE Closed stores Med. 2",
      "Offsite",
      "Offsite Iconographic",
      "Unrequestable Arch. & MSS"
    )

    forAll(testCases) {
      SierraPhysicalLocationType.fromName(id, _) shouldBe Some(
        LocationType.ClosedStores
      )
    }
  }

  it("maps names to OpenShelves") {
    val testCases = Table(
      "name",
      "Biographies",
      "Folios",
      "History of Medicine",
      "Journals",
      "Medical Collection",
      "Medicine & Society Collection",
      "Open shelves",
      "Quick Ref. Collection",
      "Rare Materials Room",
      "Student Coll (Med Lit)",
      "Student Coll. (ref only)"
    )

    forAll(testCases) {
      SierraPhysicalLocationType.fromName(id, _) shouldBe Some(
        LocationType.OpenShelves
      )
    }
  }

  it("maps to the OnExhibition type") {
    SierraPhysicalLocationType.fromName(id, "On Exhibition") shouldBe Some(
      LocationType.OnExhibition
    )
  }

  it("returns None if it can't pick a LocationType") {
    val testCases = Table(
      "name",
      "Digitised Collections",
      "none",
      "sgmip",
      "bound in above"
    )

    forAll(testCases) {
      SierraPhysicalLocationType.fromName(id, _) shouldBe None
    }
  }
}
