package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.LocationType.{
  ClosedStores,
  OnlineResource,
  OpenShelves
}
import uk.ac.wellcome.models.work.internal.{Holdings, Item}
import uk.ac.wellcome.platform.transformer.sierra.generators.MarcGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.{
  FixedField,
  MarcSubfield,
  SierraHoldingsData,
  VarField
}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  DigitalLocation
}
import weco.catalogue.sierra_adapter.generators.SierraGenerators
import weco.catalogue.sierra_adapter.models.SierraHoldingsNumber

class SierraHoldingsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraGenerators {
  it("an empty map becomes an empty list of items and holdings") {
    getItems(holdingsDataMap = Map.empty) shouldBe empty
    getHoldings(holdingsDataMap = Map.empty) shouldBe empty
  }

  describe("creates digital items if fixed field 40 = 'elro'") {
    it("does not create items if there are no instances of field 856") {
      // This example is based on b1017055 / h1068096
      val varFields = List(
        VarField(
          content = Some("00000ny   22000003n 4500"),
          fieldTag = Some("_")
        ),
        VarField(
          content = Some("HighWire - Free Full Text"),
          fieldTag = Some("p")
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty
      getHoldings(dataMap) shouldBe empty
    }

    it("creates a single digital item based on field 856") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe List(
        Item(
          id = IdState.Unidentifiable,
          locations = List(
            DigitalLocation(
              url = "https://resolver.example.com/journal",
              locationType = OnlineResource,
              linkText = Some("Connect to Example Journals"),
              accessConditions = List(
                AccessCondition(
                  status = Some(AccessStatus.LicensedResources)
                )
              )
            )
          )
        )
      )
      getHoldings(dataMap) shouldBe empty
    }

    it(
      "creates multiple items based on multiple instance of 856 on the same holdings") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        ),
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://example.org/subscriptions")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe List(
        Item(
          id = IdState.Unidentifiable,
          locations = List(
            DigitalLocation(
              url = "https://resolver.example.com/journal",
              locationType = OnlineResource,
              linkText = Some("Connect to Example Journals"),
              accessConditions = List(
                AccessCondition(
                  status = Some(AccessStatus.LicensedResources)
                )
              )
            )
          )
        ),
        Item(
          id = IdState.Unidentifiable,
          locations = List(
            DigitalLocation(
              url = "https://example.org/subscriptions",
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  status = Some(AccessStatus.LicensedResources)
                )
              )
            )
          )
        )
      )
      getHoldings(dataMap) shouldBe empty
    }

    it("creates multiple items based on multiple holdings records") {
      val varFields1 = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val varFields2 = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://example.org/subscriptions")
          )
        )
      )

      val dataMap = Map("1000001" -> varFields1, "2000002" -> varFields2)
        .map {
          case (id, varFields) =>
            SierraHoldingsNumber(id) -> SierraHoldingsData(
              fixedFields =
                Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
              varFields = varFields
            )
        }

      getItems(dataMap) shouldBe List(
        Item(
          id = IdState.Unidentifiable,
          locations = List(
            DigitalLocation(
              url = "https://resolver.example.com/journal",
              locationType = OnlineResource,
              linkText = Some("Connect to Example Journals"),
              accessConditions = List(
                AccessCondition(
                  status = Some(AccessStatus.LicensedResources)
                )
              )
            )
          )
        ),
        Item(
          id = IdState.Unidentifiable,
          locations = List(
            DigitalLocation(
              url = "https://example.org/subscriptions",
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  status = Some(AccessStatus.LicensedResources)
                )
              )
            )
          )
        )
      )
      getHoldings(dataMap) shouldBe empty
    }

    it("skips field 856 if fixed field 40 is not 'elro'") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty
      getHoldings(dataMap) shouldBe empty
    }

    it("ignores electronic holdings that are deleted") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://deleted.example.org/journal")
          )
        )
      )

      val deletedHoldingsData = SierraHoldingsData(
        deleted = true,
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )

      val undeletedHoldingsData = deletedHoldingsData.copy(deleted = false)

      // We transform with deleted = true and deleted = false, so we know
      // the holdings isn't being skipped because it's an incomplete record.
      getItems(Map(createSierraHoldingsNumber -> deletedHoldingsData)) shouldBe empty
      getItems(Map(createSierraHoldingsNumber -> undeletedHoldingsData)) should not be empty

      getHoldings(Map(createSierraHoldingsNumber -> deletedHoldingsData)) shouldBe empty
    }

    it("ignores electronic holdings that are suppressed") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(
              tag = "u",
              content = "https://suppressed.example.org/journal")
          )
        )
      )

      val suppressedHoldingsData = SierraHoldingsData(
        suppressed = true,
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )

      val unsuppressedHoldingsData =
        suppressedHoldingsData.copy(suppressed = false)

      // We transform with suppressed = true and suppressed = false, so we know
      // the holdings isn't being skipped because it's an incomplete record.
      getItems(Map(createSierraHoldingsNumber -> suppressedHoldingsData)) shouldBe empty
      getItems(Map(createSierraHoldingsNumber -> unsuppressedHoldingsData)) should not be empty

      getHoldings(Map(createSierraHoldingsNumber -> suppressedHoldingsData)) shouldBe empty
    }
  }

  describe("creates physical holdings") {
    it("does not create holdings if there is no useful data") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "989",
          subfields = List(
            MarcSubfield(tag = "a", content = "This is old location data")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        suppressed = true,
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty
      getHoldings(dataMap) shouldBe empty
    }

    it("uses the description from 866 subfield ǂa") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "Vol. 3 only")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe List("Vol. 3 only")
      holdings.head.note shouldBe None
    }

    it("uses the note from 866 subfield ǂz") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "z", content = "Another note about the document")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe empty
      holdings.head.note shouldBe Some("Another note about the document")
    }

    it("uses both the note and the description") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "Missing Vol. 2"),
            MarcSubfield(
              tag = "z",
              content = "Lost in a mysterious fishing accident")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe List("Missing Vol. 2")
      holdings.head.note shouldBe Some("Lost in a mysterious fishing accident")
    }

    it("creates an enumeration based on the contents of 85X/86X") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "1.1"),
            MarcSubfield(tag = "a", content = "57-59"),
            MarcSubfield(tag = "b", content = "4-1")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "1.2"),
            MarcSubfield(tag = "a", content = "60-61"),
            MarcSubfield(tag = "b", content = "3-2")
          )
        ),
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "1"),
            MarcSubfield(tag = "a", content = "v."),
            MarcSubfield(tag = "b", content = "no.")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe List(
        "v.57:no.4 - v.59:no.1",
        "v.60:no.3 - v.61:no.2"
      )
    }

    it("uses the location type from fixed field 40 (closed stores)") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "A secret holdings")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      val locations = holdings.head.locations
      locations should have size 1
      locations.head.locationType shouldBe ClosedStores
      locations.head.label shouldBe "Closed stores"
    }

    it("uses the location type from fixed field 40 (open shelves)") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "Journals on the shelves")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "wgser")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      val locations = holdings.head.locations
      locations should have size 1
      locations.head.locationType shouldBe OpenShelves
      locations.head.label shouldBe "Journals"
    }

    it("uses 949 subfield ǂa as the shelfmark") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "Journals on the shelves")
          )
        ),
        createVarFieldWith(
          marcTag = "949",
          subfields = List(
            MarcSubfield(tag = "a", content = "/MED     ")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "wgser")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      val locations = holdings.head.locations
      locations should have size 1
      locations.head.shelfmark shouldBe Some("/MED")
    }

    it("skips adding a location if the location code is unrecognised") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "Journals on the shelves")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "zzzzz")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      holdings.head.locations shouldBe empty
    }

    it("creates multiple holdings based on multiple data blocks") {
      val dataMap = (1 to 3).map { volno =>
        val varFields = List(
          createVarFieldWith(
            marcTag = "866",
            subfields = List(
              MarcSubfield(tag = "a", content = s"Vol. $volno only")
            )
          )
        )

        val holdingsData = SierraHoldingsData(
          fixedFields =
            Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
          varFields = varFields
        )

        SierraHoldingsNumber(s"${volno}00000$volno") -> holdingsData
      }.toMap

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 3
      holdings.map { _.enumeration } shouldBe Seq(
        List("Vol. 1 only"),
        List("Vol. 2 only"),
        List("Vol. 3 only"),
      )
    }

    it("de-duplicates holdings after transformation") {
      val dataMap = (1 to 3).map { _ =>
        val varFields = List(
          createVarFieldWith(
            marcTag = "866",
            subfields = List(
              MarcSubfield(tag = "a", content = "Complete set")
            )
          )
        )

        val holdingsData = SierraHoldingsData(
          fixedFields =
            Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
          varFields = varFields
        )

        createSierraHoldingsNumber -> holdingsData
      }.toMap

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
    }

    it("skips holdings that are deleted") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "A deleted holdings")
          )
        )
      )

      val deletedHoldingsData = SierraHoldingsData(
        deleted = true,
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )

      val undeletedHoldingsData = deletedHoldingsData.copy(deleted = false)

      // We transform with deleted = true and deleted = false, so we know
      // the holdings isn't being skipped because it's an incomplete record.
      getHoldings(Map(createSierraHoldingsNumber -> deletedHoldingsData)) shouldBe empty
      getHoldings(Map(createSierraHoldingsNumber -> undeletedHoldingsData)) should not be empty

      getItems(Map(createSierraHoldingsNumber -> deletedHoldingsData)) shouldBe empty
    }

    it("skips holdings that are suppressed") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "A suppressed holdings")
          )
        )
      )

      val suppressedHoldingsData = SierraHoldingsData(
        suppressed = true,
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )

      val unsuppressedHoldingsData =
        suppressedHoldingsData.copy(suppressed = false)

      // We transform with suppressed = true and suppressed = false, so we know
      // the holdings isn't being skipped because it's an incomplete record.
      getHoldings(Map(createSierraHoldingsNumber -> suppressedHoldingsData)) shouldBe empty
      getHoldings(Map(createSierraHoldingsNumber -> unsuppressedHoldingsData)) should not be empty

      getItems(Map(createSierraHoldingsNumber -> suppressedHoldingsData)) shouldBe empty
    }
  }

  private def getItems(
    holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData])
    : List[Item[IdState.Unminted]] = {
    val (items, _) = SierraHoldings(createSierraBibNumber, holdingsDataMap)
    items
  }

  private def getHoldings(
    holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData])
    : List[Holdings] = {
    val (_, holdings) = SierraHoldings(createSierraBibNumber, holdingsDataMap)
    holdings
  }
}
