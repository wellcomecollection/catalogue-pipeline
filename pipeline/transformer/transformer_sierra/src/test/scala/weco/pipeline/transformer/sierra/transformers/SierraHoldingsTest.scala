package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.LocationType.{
  ClosedStores,
  OnlineResource,
  OpenShelves
}
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  DigitalLocation,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.Holdings
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.sierra.models.data.SierraHoldingsData
import weco.sierra.models.identifiers.SierraHoldingsNumber
import weco.sierra.models.marc.{FixedField, Subfield, VarField}

class SierraHoldingsTest
    extends AnyFunSpec
    with Matchers
    with SierraRecordGenerators {
  it("an empty map becomes an empty list of items and holdings") {
    getHoldings(holdingsDataMap = Map.empty) shouldBe empty
  }

  describe("creates digital holdings if fixed field 40 = 'elro'") {
    it("creates nothing if there are no instances of field 856") {
      // This example is based on b1017055 / h1068096
      val varFields = List(
        VarField(
          content = "00000ny   22000003n 4500",
          fieldTag = "_"
        ),
        VarField(
          content = "HighWire - Free Full Text",
          fieldTag = "p"
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getHoldings(dataMap) shouldBe empty
    }

    it("creates a single holdings based on field 856") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            Subfield(tag = "z", content = "Connect to Example Journals")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "1.1"),
            Subfield(tag = "i", content = "1991-2017"),
            Subfield(tag = "j", content = "02-04"),
            Subfield(tag = "k", content = "01-17"),
            Subfield(
              tag = "x",
              content = "Chronology adjusted by embargo period"),
          )
        ),
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "1"),
            Subfield(tag = "i", content = "(year)"),
            Subfield(tag = "j", content = "(month)"),
            Subfield(tag = "k", content = "(day)"),
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getHoldings(dataMap) shouldBe List(
        Holdings(
          note = None,
          enumeration = List("1 Feb. 1991 - 17 Apr. 2017"),
          location = Some(
            DigitalLocation(
              url = "https://resolver.example.com/journal",
              locationType = OnlineResource,
              linkText = Some("Connect to Example Journals"),
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources())
              )
            )
          )
        )
      )
    }

    it(
      "creates multiple holdings based on multiple instance of 856 on the same holdings") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            Subfield(tag = "z", content = "Connect to Example Journals")
          )
        ),
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/subscriptions")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "elro ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getHoldings(dataMap) shouldBe List(
        Holdings(
          note = None,
          location = Some(
            DigitalLocation(
              url = "https://resolver.example.com/journal",
              locationType = OnlineResource,
              linkText = Some("Connect to Example Journals"),
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources())
              )
            )
          ),
          enumeration = List()
        ),
        Holdings(
          note = None,
          location = Some(
            DigitalLocation(
              url = "https://example.org/subscriptions",
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources())
              )
            )
          ),
          enumeration = List()
        )
      )
    }

    it("creates multiple holdings based on multiple holdings records") {
      val varFields1 = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            Subfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val varFields2 = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://example.org/subscriptions")
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

      getHoldings(dataMap) shouldBe List(
        Holdings(
          note = None,
          location = Some(
            DigitalLocation(
              url = "https://resolver.example.com/journal",
              locationType = OnlineResource,
              linkText = Some("Connect to Example Journals"),
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources())
              )
            )
          ),
          enumeration = List()
        ),
        Holdings(
          note = None,
          location = Some(
            DigitalLocation(
              url = "https://example.org/subscriptions",
              locationType = OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources())
              )
            )
          ),
          enumeration = List()
        )
      )
    }

    it("combines holdings if they have the same URL but distinct data") {
      // This test is based on holdings c10885389 and c11142145, which
      // are both linked to bib b25314166
      val holdingsData1 = SierraHoldingsData(
        fixedFields = Map(
          "40" -> FixedField(label = "LOCATION", value = "elro ")
        ),
        varFields = List(
          VarField(
            marcTag = "863",
            subfields = List(
              Subfield(tag = "8", content = "1.1"),
              Subfield(tag = "i", content = "1787-1789"),
              Subfield(tag = "j", content = "01-12"),
              Subfield(tag = "k", content = "01-31")
            )
          ),
          VarField(
            marcTag = "853",
            subfields = List(
              Subfield(tag = "8", content = "1"),
              Subfield(tag = "i", content = "(year)"),
              Subfield(tag = "j", content = "(month)"),
              Subfield(tag = "k", content = "(day)")
            )
          ),
          VarField(
            marcTag = "856",
            subfields = List(
              Subfield(tag = "u", content = "http://example.org/journal"),
              Subfield(
                tag = "z",
                content =
                  "Connect to 17th-18th Century Burney Collection newspapers")
            )
          )
        )
      )

      val holdingsData2 = SierraHoldingsData(
        fixedFields = Map(
          "40" -> FixedField(label = "LOCATION", value = "elro ")
        ),
        varFields = List(
          VarField(
            marcTag = "863",
            subfields = List(
              Subfield(tag = "8", content = "1.1"),
              Subfield(tag = "i", content = "1787-1789"),
              Subfield(tag = "j", content = "01-12"),
              Subfield(tag = "k", content = "01-31")
            )
          ),
          VarField(
            marcTag = "853",
            subfields = List(
              Subfield(tag = "8", content = "1"),
              Subfield(tag = "i", content = "(year)"),
              Subfield(tag = "j", content = "(month)"),
              Subfield(tag = "k", content = "(day)")
            )
          ),
          VarField(
            marcTag = "856",
            subfields = List(
              Subfield(tag = "u", content = "http://example.org/journal"),
              Subfield(
                tag = "z",
                content =
                  "Universal London Price Current -- Seventeenth and Eighteenth Century Burney Newspapers Collection")
            )
          )
        )
      )

      val holdings = getHoldings(
        Map(
          createSierraHoldingsNumber -> holdingsData1,
          createSierraHoldingsNumber -> holdingsData2
        )
      )

      holdings shouldBe List(
        Holdings(
          note = Some(
            "Universal London Price Current -- Seventeenth and Eighteenth Century Burney Newspapers Collection"),
          enumeration = List("1 Jan. 1787 - 31 Dec. 1789"),
          location = Some(
            DigitalLocation(
              url = "http://example.org/journal",
              linkText = Some(
                "Connect to 17th-18th Century Burney Collection newspapers"),
              locationType = LocationType.OnlineResource,
              accessConditions = List(
                AccessCondition(
                  method = AccessMethod.ViewOnline,
                  status = AccessStatus.LicensedResources())
              )
            )
          )
        )
      )
    }

    it("skips field 856 if fixed field 40 is not 'elro'") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(
              tag = "u",
              content = "https://resolver.example.com/journal"),
            Subfield(tag = "z", content = "Connect to Example Journals")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      getHoldings(dataMap) shouldBe empty
    }

    it("ignores electronic holdings that are deleted") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(tag = "u", content = "https://deleted.example.org/journal")
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
      getHoldings(Map(createSierraHoldingsNumber -> deletedHoldingsData)) shouldBe empty
      getHoldings(Map(createSierraHoldingsNumber -> undeletedHoldingsData)) should not be empty
    }

    it("ignores electronic holdings that are suppressed") {
      val varFields = List(
        VarField(
          marcTag = "856",
          subfields = List(
            Subfield(
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
      getHoldings(Map(createSierraHoldingsNumber -> suppressedHoldingsData)) shouldBe empty
      getHoldings(Map(createSierraHoldingsNumber -> unsuppressedHoldingsData)) should not be empty
    }
  }

  describe("creates physical holdings") {
    it("does not create holdings if there is no useful data") {
      val varFields = List(
        VarField(
          marcTag = "989",
          subfields = List(
            Subfield(tag = "a", content = "This is old location data")
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

      getHoldings(dataMap) shouldBe empty
    }

    it("uses the description from 866 subfield ǂa") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "Vol. 3 only")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe List("Vol. 3 only")
      holdings.head.note shouldBe None
    }

    it("uses the note from 866 subfield ǂz") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "z", content = "Another note about the document")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe empty
      holdings.head.note shouldBe Some("Another note about the document")
    }

    it("uses both the note and the description") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "Missing Vol. 2"),
            Subfield(
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

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe List("Missing Vol. 2")
      holdings.head.note shouldBe Some("Lost in a mysterious fishing accident")
    }

    it("creates an enumeration based on the contents of 85X/86X") {
      val varFields = List(
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "1.1"),
            Subfield(tag = "a", content = "57-59"),
            Subfield(tag = "b", content = "4-1")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "1.2"),
            Subfield(tag = "a", content = "60-61"),
            Subfield(tag = "b", content = "3-2")
          )
        ),
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "1"),
            Subfield(tag = "a", content = "v."),
            Subfield(tag = "b", content = "no.")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1
      holdings.head.enumeration shouldBe List(
        "v.57:no.4 - v.59:no.1",
        "v.60:no.3 - v.61:no.2"
      )
    }

    it("uses the location type from fixed field 40 (closed stores)") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "A secret holdings")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "stax ")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      holdings.head.location shouldBe Some(
        PhysicalLocation(
          locationType = ClosedStores,
          label = ClosedStores.label
        )
      )
    }

    it("uses the location type from fixed field 40 (open shelves)") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "Journals on the shelves")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "wgser")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      holdings.head.location shouldBe Some(
        PhysicalLocation(
          locationType = OpenShelves,
          label = "Journals"
        )
      )
    }

    it("uses 949 subfield ǂa as the shelfmark") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "Journals on the shelves")
          )
        ),
        VarField(
          marcTag = "949",
          subfields = List(
            Subfield(tag = "a", content = "/MED     ")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "wgser")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      holdings.head.location.get
        .asInstanceOf[PhysicalLocation]
        .shelfmark shouldBe Some("/MED")
    }

    it("skips adding a location if the location code is unrecognised") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "Journals on the shelves")
          )
        )
      )

      val holdingsData = SierraHoldingsData(
        fixedFields =
          Map("40" -> FixedField(label = "LOCATION", value = "zzzzz")),
        varFields = varFields
      )
      val dataMap = Map(createSierraHoldingsNumber -> holdingsData)

      val holdings = getHoldings(dataMap)
      holdings should have size 1

      holdings.head.location shouldBe None
    }

    it("creates multiple holdings based on multiple data blocks") {
      val dataMap = (1 to 3).map { volno =>
        val varFields = List(
          VarField(
            marcTag = "866",
            subfields = List(
              Subfield(tag = "a", content = s"Vol. $volno only")
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
          VarField(
            marcTag = "866",
            subfields = List(
              Subfield(tag = "a", content = "Complete set")
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

      val holdings = getHoldings(dataMap)
      holdings should have size 1
    }

    it("skips holdings that are deleted") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "A deleted holdings")
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
    }

    it("skips holdings that are suppressed") {
      val varFields = List(
        VarField(
          marcTag = "866",
          subfields = List(
            Subfield(tag = "a", content = "A suppressed holdings")
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
    }
  }

  private def getHoldings(
    holdingsDataMap: Map[SierraHoldingsNumber, SierraHoldingsData])
    : List[Holdings] =
    SierraHoldings(createSierraBibNumber, holdingsDataMap)
}
