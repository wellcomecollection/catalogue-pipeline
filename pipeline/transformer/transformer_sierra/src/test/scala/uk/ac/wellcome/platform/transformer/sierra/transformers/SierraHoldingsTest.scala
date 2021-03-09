package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.IdState.Unidentifiable
import uk.ac.wellcome.models.work.internal.LocationType.OnlineResource
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  AccessStatus,
  DigitalLocation,
  Holdings,
  IdState,
  Item
}
import uk.ac.wellcome.platform.transformer.sierra.generators.MarcGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.{
  FixedField,
  MarcSubfield,
  SierraHoldingsData,
  VarField
}
import weco.catalogue.sierra_adapter.generators.SierraGenerators
import weco.catalogue.sierra_adapter.models.SierraHoldingsNumber

class SierraHoldingsTest extends AnyFunSpec with Matchers with MarcGenerators with SierraGenerators {
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
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
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
            MarcSubfield(tag = "u", content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe List(
        Item(
          id = Unidentifiable,
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

    it("creates multiple items based on multiple instance of 856 on the same holdings") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(tag = "u", content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        ),
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(tag = "u", content = "https://example.org/subscriptions")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe List(
        Item(
          id = Unidentifiable,
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
          id = Unidentifiable,
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
            MarcSubfield(tag = "u", content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val varFields2 = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(tag = "u", content = "https://example.org/subscriptions")
          )
        )
      )

      val dataMap = Map("1000001" -> varFields1, "2000002" -> varFields2)
        .map { case (id, varFields) =>
          SierraHoldingsNumber(id) -> SierraHoldingsData(
            fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
            varFields = varFields
          )
        }

      getItems(dataMap) shouldBe List(
        Item(
          id = Unidentifiable,
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
          id = Unidentifiable,
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
            MarcSubfield(tag = "u", content = "https://resolver.example.com/journal"),
            MarcSubfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
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
            MarcSubfield(tag = "u", content = "https://deleted.example.org/journal")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        deleted = true,
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty
      getHoldings(dataMap) shouldBe empty
    }

    it("ignores electronic holdings that are suppressed") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "856",
          subfields = List(
            MarcSubfield(tag = "u", content = "https://suppressed.example.org/journal")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        suppressed = true,
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty
      getHoldings(dataMap) shouldBe empty
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
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
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
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.description shouldBe Some("Vol. 3 only")
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
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.description shouldBe None
      holdings.head.note shouldBe Some("Another note about the document")
    }

    it("sets both the note and the description") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "866",
          subfields = List(
            MarcSubfield(tag = "a", content = "Missing Vol. 2"),
            MarcSubfield(tag = "z", content = "Lost in a mysterious fishing accident")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields = Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getItems(dataMap) shouldBe empty

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.description shouldBe Some("Missing Vol. 2")
      holdings.head.note shouldBe Some("Lost in a mysterious fishing accident")
    }
  }

  private def getItems(holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): List[Item[IdState.Unminted]] = {
    val (items, _) = SierraHoldings(createSierraBibNumber, holdingsDataMap)
    items
  }

  private def getHoldings(holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData]): List[Holdings] = {
    val (_, holdings) = SierraHoldings(createSierraBibNumber, holdingsDataMap)
    holdings
  }
}
