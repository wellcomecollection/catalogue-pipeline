package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  LocationType,
  PhysicalLocation
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.SierraItemData
import weco.sierra.models.fields.SierraLocation
import weco.sierra.models.marc.{FixedField, Subfield, VarField}

class SierraPhysicalLocationTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  private val transformer = new SierraPhysicalLocation {}

  describe("Physical locations") {
    val bibData = createSierraBibData

    val itemData = createSierraItemDataWith(
      location = Some(SierraLocation("sgmed", "Closed stores Med."))
    )

    it("extracts location from item data") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraLocation("sgmed", "Closed stores Med.")),
        fixedFields = Map(
          "79" -> FixedField(
            label = "LOCATION",
            value = "scmac",
            display = "Closed stores Arch. & MSS"
          ),
          "88" -> FixedField(
            label = "STATUS",
            value = "-",
            display = "Available"
          ),
          "108" -> FixedField(
            label = "OPACMSG",
            value = "f",
            display = "Online request"
          )
        )
      )

      val expectedLocation = PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label,
        accessConditions = List(
          AccessCondition(
            method = AccessMethod.OnlineRequest,
            status = AccessStatus.Open
          )
        )
      )

      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        expectedLocation
      )
    }

    it("uses the name as the label for non-closed locations") {
      val itemData: SierraItemData = createSierraItemDataWith(
        location = Some(SierraLocation("wghxg", "Folios"))
      )

      val location =
        transformer.getPhysicalLocation(itemData, bibData).get
      location.label shouldBe "Folios"
    }

    it("returns None if the location field only contains empty strings") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraLocation("", ""))
      )

      transformer.getPhysicalLocation(itemData, bibData) shouldBe None
    }

    it("returns None if the location field only contains the string 'none'") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraLocation("none", "none"))
      )
      transformer.getPhysicalLocation(itemData, bibData) shouldBe None
    }

    it("returns None if there is no location in the item data") {
      val itemData = createSierraItemDataWith(
        location = None
      )

      transformer.getPhysicalLocation(itemData, bibData) shouldBe None
    }

    it("adds access conditions to the items") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = "506",
            subfields = List(
              Subfield("a", "You can look at this"),
              Subfield("f", "Open")
            )
          )
        )
      )

      val itemData: SierraItemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> FixedField(
            label = "LOCATION",
            value = "scmac",
            display = "Closed stores Arch. & MSS"
          ),
          "88" -> FixedField(
            label = "STATUS",
            value = "-",
            display = "Available"
          ),
          "108" -> FixedField(
            label = "OPACMSG",
            value = "f",
            display = "Online request"
          )
        ),
        location = Some(
          SierraLocation(code = "sgmed", name = "Closed stores Med.")
        )
      )

      val location =
        transformer.getPhysicalLocation(itemData, bibData).get
      location.accessConditions shouldBe List(
        AccessCondition(
          method = AccessMethod.OnlineRequest,
          status = Some(AccessStatus.Open)
        )
      )
    }

    it("uses 949 subfield ǂa as the shelfmark") {
      val itemData: SierraItemData = createSierraItemDataWith(
        location = Some(SierraLocation("info", "Open shelves")),
        varFields = List(
          VarField(
            marcTag = Some("949"),
            fieldTag = Some("c"),
            subfields = List(
              Subfield(tag = "a", content = "AX1234:Box 1")
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(itemData, bibData).get

      location.shelfmark shouldBe Some("AX1234:Box 1")
    }

    describe("uses fallback locations") {
      it("returns an empty location if location name is 'bound in above'") {
        val result = transformer.getPhysicalLocation(
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraLocation("bwith", "bound in above"))
          )
        )

        result shouldBe None
      }

      it("uses the fallback location if location name is 'bound in above'") {
        val result = transformer.getPhysicalLocation(
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraLocation("bwith", "bound in above"))
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
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraLocation("cwith", "contained in above"))
          )
        )

        result shouldBe None
      }

      it(
        "uses the fallback location if location name is 'contained in above'"
      ) {
        val result = transformer.getPhysicalLocation(
          bibData = createSierraBibData,
          itemData = createSierraItemDataWith(
            location = Some(SierraLocation("cwith", "contained in above"))
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
            marcTag = "506",
            subfields = List(
              Subfield("a", "You're not allowed yet"),
              Subfield("g", "2099-12-31")
            )
          )
        )
      )

      val location =
        transformer
          .getPhysicalLocation(itemData = itemData, bibData = bibData)
          .get

      location.accessConditions shouldBe List(
        AccessCondition(
          method = AccessMethod.NotRequestable,
          note = Some(
            s"""This item cannot be requested online. Please contact <a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a> for more information."""
          )
        )
      )
    }
  }
}
