package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  AccessStatus,
  DigitalLocationDeprecated,
  LocationType,
  PhysicalLocationDeprecated
}
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators

class SierraLocationDeprecatedTest
    extends AnyFunSpec
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
      val expectedLocation = PhysicalLocationDeprecated(locationType, label)
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
        PhysicalLocationDeprecated(
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
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocationDeprecated(
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
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocationDeprecated(
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
      transformer.getPhysicalLocation(itemData, bibData) shouldBe Some(
        PhysicalLocationDeprecated(
          locationType = locationType,
          label = label,
          accessConditions = List()
        )
      )
    }

  }

  describe("Digital locations") {
    it("returns a digital location based on the id") {
      val id = "b2201508"
      val expectedLocation = DigitalLocationDeprecated(
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
