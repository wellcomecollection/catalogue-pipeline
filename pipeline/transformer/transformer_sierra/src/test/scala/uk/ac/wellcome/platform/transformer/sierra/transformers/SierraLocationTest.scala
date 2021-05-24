package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.source_model.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}
import weco.catalogue.source_model.sierra.source.SierraSourceLocation
import weco.catalogue.source_model.sierra.SierraItemData

class SierraLocationTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  private val transformer = new SierraLocation {}

  describe("Physical locations") {
    val bibId = createSierraBibNumber
    val itemId = createSierraItemNumber
    val bibData = createSierraBibData

    val locationType = LocationType.ClosedStores
    val label = LocationType.ClosedStores.label
    val itemData = createSierraItemDataWith(
      location = Some(SierraSourceLocation("sgmed", "Closed stores Med."))
    )

    it("extracts location from item data") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("sgmed", "Closed stores Med."))
      )

      val expectedLocation = PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label
      )

      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe Some(
        expectedLocation)
    }

    it("uses the name as the label for non-closed locations") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("wghxg", "Folios"))
      )

      val expectedLocation = PhysicalLocation(
        locationType = LocationType.OpenShelves,
        label = "Folios"
      )

      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe Some(
        expectedLocation)
    }

    it("returns None if the location field only contains empty strings") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("", ""))
      )

      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe None
    }

    it("returns None if the location field only contains the string 'none'") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("none", "none"))
      )
      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe None
    }

    it("returns None if there is no location in the item data") {
      val itemData = createSierraItemDataWith(
        location = None
      )

      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe None
    }

    it("adds access status to the location if present") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "You're not allowed yet"),
              MarcSubfield("f", "Restricted")
            )
          )
        )
      )
      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List(
            AccessCondition(status = AccessStatus.Restricted),
          )
        )
      )
    }

    it("uses 949 subfield ǂa as the shelfmark") {
      val itemData: SierraItemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("info", "Open shelves")),
        varFields = List(
          createVarFieldWith(
            marcTag = "949",
            subfields = List(
              MarcSubfield(tag = "a", content = "AX1234:Box 1")
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get

      location.shelfmark shouldBe Some("AX1234:Box 1")
    }

    describe("uses fallback locations") {
      it("returns an empty location if location name is 'bound in above'") {
        val result = transformer.getPhysicalLocation(
          bibNumber = createSierraBibNumber,
          itemNumber = createSierraItemNumber,
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraSourceLocation("bwith", "bound in above"))
          )
        )

        result shouldBe None
      }

      it("uses the fallback location if location name is 'bound in above'") {
        val result = transformer.getPhysicalLocation(
          bibNumber = createSierraBibNumber,
          itemNumber = createSierraItemNumber,
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraSourceLocation("bwith", "bound in above"))
          ),
          fallbackLocation = Some(
            (LocationType.OpenShelves, "History of Medicine")
          )
        )

        result.get.locationType shouldBe LocationType.OpenShelves
        result.get.label shouldBe "History of Medicine"
      }

      it("returns an empty location if location name is 'contained in above'") {
        val result = transformer.getPhysicalLocation(
          bibNumber = createSierraBibNumber,
          itemNumber = createSierraItemNumber,
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraSourceLocation("cwith", "contained in above"))
          )
        )

        result shouldBe None
      }

      it("uses the fallback location if location name is 'contained in above'") {
        val result = transformer.getPhysicalLocation(
          bibNumber = createSierraBibNumber,
          itemNumber = createSierraItemNumber,
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraSourceLocation("cwith", "contained in above"))
          ),
          fallbackLocation = Some(
            (LocationType.OpenShelves, "History of Medicine")
          )
        )

        result.get.locationType shouldBe LocationType.OpenShelves
        result.get.label shouldBe "History of Medicine"
      }
    }

    it("adds 'Open' access condition if ind1 is 0") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(marcTag = Some("506"), indicator1 = Some("0"))
        )
      )
      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List(AccessCondition(status = AccessStatus.Open))
        )
      )
    }

    it("skips an access condition if it can't get an access status") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "You're not allowed yet"),
              MarcSubfield("g", "2099-12-31"),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get
      location.accessConditions shouldBe empty
    }

    it("sets an access status based on the contents of subfield ǂf") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "You're not allowed yet"),
              MarcSubfield("f", "Restricted"),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.status shouldBe Some(
        AccessStatus.Restricted)
    }

    it(
      "sets an access status based on the contents of subfield ǂa if ǂf is missing") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "Restricted"),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.status shouldBe Some(
        AccessStatus.Restricted)
      location.accessConditions.head.terms shouldBe None
    }

    it("only sets an AccessStatus if the contents of ǂa and ǂf agree") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "Restricted"),
              MarcSubfield("f", "Open"),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get
      location.accessConditions shouldBe empty
    }

    it(
      "does not add an access condition if none of the relevant subfields are present") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("e", "Something something"),
            )
          )
        )
      )
      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List()
        )
      )
    }
  }
}
