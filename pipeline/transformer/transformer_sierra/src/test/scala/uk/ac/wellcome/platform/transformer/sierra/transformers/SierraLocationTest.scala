package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  AccessStatus,
  DigitalLocation,
  LocationType,
  PhysicalLocation
}
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators

class SierraLocationTest
    extends FunSpec
    with Matchers
    with SierraDataGenerators {

  private val transformer = new SierraLocation {}

  describe("Physical locations") {

    val bibData = createSierraBibData

    val locationType = LocationType("sgmed")
    val label = "A museum of mermaids"
    val itemData = createSierraItemDataWith(
      location = Some(SierraSourceLocation("sgmed", label))
    )

    it("extracts location from item data") {
      val expectedLocation = PhysicalLocation(locationType, label)
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        expectedLocation)
    }

    it("returns None if the location field only contains empty strings") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("", ""))
      )

      transformer.getPhysicalLocation(itemData, bibData) shouldBe None
    }

    it("returns None if the location field only contains the string 'none'") {
      val itemData = createSierraItemDataWith(
        location = Some(SierraSourceLocation("none", "none"))
      )
      transformer.getPhysicalLocation(itemData, bibData) shouldBe None
    }

    it("returns None if there is no location in the item data") {
      val itemData = createSierraItemDataWith(
        location = None
      )

      transformer.getPhysicalLocation(itemData, bibData) shouldBe None
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
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = Some(
            List(
              AccessCondition(
                status = AccessStatus.Restricted,
                terms = Some("You're not allowed yet"),
                to = Some("2099-12-31")
              ),
            )
          )
        )
      )
    }

    it("strips punctuation from access condition if present") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("f", "Open."),
              MarcSubfield("g", "2099-12-31"),
            )
          )
        )
      )
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = Some(
            List(
              AccessCondition(
                status = AccessStatus.Open,
                to = Some("2099-12-31")
              ),
            )
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
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions = Some(List(AccessCondition(AccessStatus.Open)))
        )
      )
    }

    it(
      "maps Restricted access (Data Protection Act) to restricted access status") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("f", "Restricted access (Data Protection Act)")
            )
          )
        )
      )
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions =
            Some(List(AccessCondition(AccessStatus.Restricted)))
        )
      )
    }

    it("maps Cannot Be Produced. to restricted access status") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("f", "Cannot Be Produced.")
            )
          )
        )
      )
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions =
            Some(List(AccessCondition(AccessStatus.Restricted)))
        )
      )
    }

    it("maps Certain restrictions apply. to restricted access status") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(
              MarcSubfield("f", "Certain restrictions apply.")
            )
          )
        )
      )
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocation(
          locationType = locationType,
          label = label,
          accessConditions =
            Some(List(AccessCondition(AccessStatus.Restricted)))
        )
      )
    }

    it("fails if invalid AccessStatus") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          VarField(
            marcTag = Some("506"),
            subfields = List(MarcSubfield("f", "Oopsy"))
          )
        )
      )
      assertThrows[Exception] {
        transformer.getPhysicalLocation(itemData, bibData)
      }
    }
  }

  describe("Digital locations") {
    it("returns a digital location based on the id") {
      val id = "b2201508"
      val expectedLocation = DigitalLocation(
        url = "https://wellcomelibrary.org/iiif/b2201508/manifest",
        license = None,
        locationType = LocationType("iiif-presentation")
      )
      transformer.getDigitalLocation(id) shouldBe expectedLocation
    }

    it("throws an exception if no resource identifier is supplied") {
      val caught = intercept[SierraTransformerException] {
        transformer.getDigitalLocation(identifier = "")
      }
      caught.e.getMessage shouldEqual "id required by DigitalLocation has not been provided"
    }
  }
}
