package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.AccessStatus.LicensedResources
import weco.catalogue.internal_model.locations.LocationType.OnlineResource
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.sierra.generators.MarcGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraElectronicResourcesTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraRecordGenerators {
  it("returns an Item that uses the URL from 856 ǂu") {
    val varFields = List(
      VarField(
        marcTag = "856",
        subfields = List(
          Subfield(tag = "u", content = "https://example.org/journal")
        )
      )
    )

    getElectronicResources(varFields) shouldBe List(
      Item(
        title = None,
        locations = List(
          DigitalLocation(
            url = "https://example.org/journal",
            locationType = OnlineResource,
            accessConditions = List(
              AccessCondition(
                method = AccessMethod.ViewOnline,
                status = AccessStatus.LicensedResources(
                  relationship = LicensedResources.Resource
                )
              )
            )
          )
        )
      )
    )
  }

  it("returns multiple Items if field 856 is repeated") {
    val varFields = List(
      VarField(
        marcTag = "856",
        subfields = List(
          Subfield(tag = "u", content = "https://example.org/journal")
        )
      ),
      VarField(
        marcTag = "856",
        subfields = List(
          Subfield(tag = "u", content = "https://example.org/another-journal")
        )
      )
    )

    getElectronicResources(varFields) shouldBe List(
      Item(
        title = None,
        locations = List(
          DigitalLocation(
            url = "https://example.org/journal",
            locationType = OnlineResource,
            accessConditions = List(
              AccessCondition(
                method = AccessMethod.ViewOnline,
                status = AccessStatus.LicensedResources()
              )
            )
          )
        )
      ),
      Item(
        title = None,
        locations = List(
          DigitalLocation(
            url = "https://example.org/another-journal",
            locationType = OnlineResource,
            accessConditions = List(
              AccessCondition(
                method = AccessMethod.ViewOnline,
                status = AccessStatus.LicensedResources()
              )
            )
          )
        )
      )
    )
  }

  it("marks the item as a related resource if 856 ind2 = 2") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "856",
        indicator2 = "2",
        subfields = List(
          Subfield(tag = "u", content = "https://example.org/journal")
        )
      )
    )

    getElectronicResources(varFields) shouldBe List(
      Item(
        title = None,
        locations = List(
          DigitalLocation(
            url = "https://example.org/journal",
            locationType = OnlineResource,
            accessConditions = List(
              AccessCondition(
                method = AccessMethod.ViewOnline,
                status = AccessStatus.LicensedResources(
                  relationship = LicensedResources.RelatedResource
                )
              )
            )
          )
        )
      )
    )
  }

  describe("sets the label") {
    it("uses the concatenated contents of 856 ǂz, ǂy and ǂ3") {
      // None of our records use all three subfields (they all use one or two),
      // but we do it here to make testing simple.
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/journal"),
            Subfield(tag = "3", content = "Related archival materials:"),
            Subfield(tag = "z", content = "available to library members."),
            Subfield(tag = "z", content = "Cambridge Books Online.")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = Some(
            "Related archival materials: available to library members. Cambridge Books Online."
          ),
          locations = List(
            DigitalLocation(
              url = "https://example.org/journal",
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }

    it(
      "puts the label in the linkText if it's ≤7 words and contains 'view', 'access' or 'connect'"
    ) {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/viewer"),
            Subfield(tag = "3", content = "View online")
          )
        ),
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/resource"),
            Subfield(tag = "z", content = "Access resource")
          )
        ),
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/journal"),
            Subfield(tag = "z", content = "Connect to journal")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/viewer",
              linkText = Some("View online"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        ),
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/resource",
              linkText = Some("Access resource"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        ),
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/journal",
              linkText = Some("Connect to journal"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }

    it(
      "puts the label in the item title if it's ≤7 words but doesn't contain 'view', 'access' or 'connect'"
    ) {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/oxford"),
            Subfield(tag = "3", content = "Oxford Libraries Online")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = Some("Oxford Libraries Online"),
          locations = List(
            DigitalLocation(
              url = "https://example.org/oxford",
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }

    it("trims whitespace from the underlying subfields") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/resource"),
            Subfield(tag = "3", content = "View resource ")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/resource",
              linkText = Some("View resource"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }

    it("strips trailing punctuation") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/resource"),
            Subfield(tag = "3", content = "View resource.")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/resource",
              linkText = Some("View resource"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }

    it("title cases the word 'view' at the start of the label") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/resource"),
            Subfield(tag = "3", content = "view resource")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/resource",
              linkText = Some("View resource"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }

    it("doesn't title case 'view' if it's not at the start of the label") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/resource"),
            Subfield(tag = "3", content = "You can view this resource online")
          )
        )
      )

      getElectronicResources(varFields) shouldBe List(
        Item(
          title = None,
          locations = List(
            DigitalLocation(
              url = "https://example.org/resource",
              linkText = Some("You can view this resource online"),
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources()
                )
              )
            )
          )
        )
      )
    }
  }

  describe("skips adding an item") {
    it("if there are no instances of field 856") {
      getElectronicResources(varFields = List()) shouldBe empty

      getElectronicResources(
        varFields = List(
          createVarFieldWith(marcTag = "855"),
          createVarFieldWith(marcTag = "857")
        )
      ) shouldBe empty
    }

    it("if 856 ǂu isn't a URL") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "search for 'online journals'")
          )
        )
      )

      getElectronicResources(varFields) shouldBe empty
    }

    it("if 856 ǂu is repeated") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/journal"),
            Subfield(tag = "u", content = "https://example.org/another-journal")
          )
        )
      )

      getElectronicResources(varFields) shouldBe empty
    }

    it("if 856 doesn't have an instance of ǂu") {
      // When we first added 856 on bibs, some of our catalogue records had
      // the URL in subfield ǂa.  Because it was only a small number of records
      // and it deviates from the MARC spec, we prefer not to handle it in
      // the transformer, and instead get it fixed in the catalogue.
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "a", content = "https://example.org/journal")
          )
        )
      )

      getElectronicResources(varFields) shouldBe empty
    }
  }

  def getElectronicResources(varFields: List[VarField]): List[Item[_]] =
    SierraElectronicResources(
      id = createSierraBibNumber,
      varFields = varFields
    )
}
