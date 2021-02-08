package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  AccessStatus,
  LocationType,
  PhysicalLocation
}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators

class SierraLocationTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  private val transformer = new SierraLocation {}

  describe("Physical locations") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val locationType = LocationType("sgmed")
    val label = "A museum of mermaids"
    val itemData = createSierraItemDataWith(
      location = Some(SierraSourceLocation("sgmed", label))
    )

    it("extracts location from item data") {
      val expectedLocation = PhysicalLocation(locationType, label)
      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe Some(
        expectedLocation)
    }

    it("returns None if the location field only contains empty strings") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("", ""))
      )

      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe None
    }

    it("returns None if the location field only contains the string 'none'") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("none", "none"))
      )
      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe None
    }

    it("returns None if there is no location in the item data") {
      val itemData = createSierraItemDataWith(
        location = None
      )

      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe None
    }

    it("adds access condition to the location if present") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "You're not allowed yet"),
              MarcSubfield("f", "Restricted"),
              MarcSubfield("g", "2099-12-31"),
            )
          )
        )
      )
      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List(
            AccessCondition(
              status = Some(AccessStatus.Restricted),
              terms = Some("You're not allowed yet"),
              to = Some("2099-12-31")
            ),
          )
        )
      )
    }

    it("adds 'Open' access condition if ind1 is 0") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(marcTag = Some("506"), indicator1 = Some("0"))
        )
      )
      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List(AccessCondition(Some(AccessStatus.Open)))
        )
      )
    }

    it("adds access condition if f is not present but other subfields are") {
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
      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List(
            AccessCondition(
              status = None,
              terms = Some("You're not allowed yet"),
              to = Some("2099-12-31")
            ),
          )
        )
      )
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
        transformer.getPhysicalLocation(bibId, itemData, bibData).get
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
        transformer.getPhysicalLocation(bibId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.status shouldBe Some(
        AccessStatus.Restricted)
      location.accessConditions.head.terms shouldBe Some("Restricted")
    }

    it("does not set an AccessStatus if the contents of ǂa and ǂf disagree") {
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
        transformer.getPhysicalLocation(bibId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.status shouldBe None
      location.accessConditions.head.terms shouldBe Some("Restricted")
    }

    it(
      "does not set an AccessStatus if the indicator 0 and the contents of subfield ǂf disagree") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            indicator1 = Some("0"),
            subfields = List(
              MarcSubfield("a", "This item is inconsistent and weird"),
              MarcSubfield("f", "Restricted"),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.status shouldBe None
      location.accessConditions.head.terms shouldBe Some(
        "This item is inconsistent and weird")
    }

    it(
      "does not set an AccessStatus if the contents of subfield ǂf can't be parsed") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "This item is inconsistent and weird"),
              MarcSubfield("f", "fffffff my cat sat on the keyboard"),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.status shouldBe None
      location.accessConditions.head.terms shouldBe Some(
        "This item is inconsistent and weird")
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
      transformer.getPhysicalLocation(bibId, itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = List()
        )
      )
    }

    it("strips whitespace from the access conditions") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("a", "Permission is required to view this item. "),
            )
          )
        )
      )

      val location =
        transformer.getPhysicalLocation(bibId, itemData, bibData).get
      location.accessConditions should have size 1

      location.accessConditions.head.terms shouldBe Some(
        "Permission is required to view this item.")
    }
  }
}
