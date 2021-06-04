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
import weco.catalogue.source_model.sierra.marc.{
  FixedField,
  MarcSubfield,
  VarField
}
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

    val itemData = createSierraItemDataWith(
      location = Some(SierraSourceLocation("sgmed", "Closed stores Med."))
    )

    it("extracts location from item data") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("sgmed", "Closed stores Med.")),
        fixedFields = Map(
          "79" -> FixedField(
            label = "LOCATION",
            value = "scmac",
            display = "Closed stores Arch. & MSS"),
          "88" -> FixedField(
            label = "STATUS",
            value = "-",
            display = "Available"),
          "108" -> FixedField(
            label = "OPACMSG",
            value = "f",
            display = "Online request"),
        )
      )

      val expectedLocation = PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label,
        accessConditions = List(AccessCondition(terms = Some("Online request")))
      )

      transformer.getPhysicalLocation(bibId, itemId, itemData, bibData) shouldBe Some(
        expectedLocation)
    }

    it("uses the name as the label for non-closed locations") {
      val itemData: SierraItemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("wghxg", "Folios"))
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get
      location.label shouldBe "Folios"
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

    it("adds access conditions to the items") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "You can look at this"),
              MarcSubfield("f", "Open")
            )
          )
        )
      )

      val itemData: SierraItemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> FixedField(
            label = "LOCATION",
            value = "scmac",
            display = "Closed stores Arch. & MSS"),
          "88" -> FixedField(
            label = "STATUS",
            value = "-",
            display = "Available"),
          "108" -> FixedField(
            label = "OPACMSG",
            value = "f",
            display = "Online request"),
        ),
        location = Some(
          SierraSourceLocation(code = "sgmed", name = "Closed stores Med.")
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemId, itemData, bibData).get
      location.accessConditions shouldBe List(
        AccessCondition(
          status = Some(AccessStatus.Open),
          terms = Some("Online request"))
      )
    }

    it("uses 949 subfield ǂa as the shelfmark") {
      val itemData: SierraItemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("info", "Open shelves")),
        varFields = List(
          VarField(
            marcTag = Some("949"),
            fieldTag = Some("c"),
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

    it("skips an access condition if it doesn't have enough information") {
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
      location.accessConditions shouldBe List(
        AccessCondition(
          status = Some(AccessStatus.TemporarilyUnavailable),
          terms = Some(
            "Please check this item on the Wellcome Library website for access information")
        )
      )
    }
  }
}
