package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  SierraItemData,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocation}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.sierra_adapter.models.SierraItemNumber

class SierraItemsTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  it("creates both forms of the Sierra ID in 'identifiers'") {
    val itemData = createSierraItemData
    val itemId = createSierraItemNumber

    val sourceIdentifier1 = createSierraSystemSourceIdentifierWith(
      ontologyType = "Item",
      value = itemId.withCheckDigit)

    val sourceIdentifier2 = SourceIdentifier(
      identifierType = IdentifierType("sierra-identifier"),
      ontologyType = "Item",
      value = itemId.withoutCheckDigit
    )

    val expectedIdentifiers = List(sourceIdentifier1, sourceIdentifier2)

    val transformedItem = getTransformedItems(
      itemDataMap = Map(itemId -> itemData)
    ).head

    transformedItem.id.allSourceIdentifiers shouldBe expectedIdentifiers
  }

  it("uses the full Sierra system number as the source identifier") {
    val itemId = createSierraItemNumber
    val sourceIdentifier = createSierraSystemSourceIdentifierWith(
      ontologyType = "Item",
      value = itemId.withCheckDigit
    )
    val itemData = createSierraItemData

    val transformedItem = getTransformedItems(
      itemDataMap = Map(itemId -> itemData)
    ).head

    transformedItem.id
      .asInstanceOf[IdState.Identifiable]
      .sourceIdentifier shouldBe sourceIdentifier
  }

  describe("gets the title") {
    it("skips the title if it can't find one") {
      val itemData = createSierraItemData

      getTitle(itemData) shouldBe None
    }

    it("uses the contents of the varfield with field tag v") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = Some("b"), content = Some("S11.1L")),
          VarField(fieldTag = Some("v"), content = Some("Envelope")),
        )
      )

      getTitle(itemData) shouldBe Some("Envelope")
    }

    it("skips instances of field tag that are empty") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = Some("b"), content = Some("S11.1L")),
          VarField(fieldTag = Some("v"), content = Some("")),
          VarField(fieldTag = Some("v"), content = Some("Envelope")),
        )
      )

      getTitle(itemData) shouldBe Some("Envelope")
    }

    it(
      "uses the contents of subfield ǂa if field tag ǂv doesn't have a contents field") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = Some("b"), content = Some("S11.1L")),
          VarField(
            fieldTag = Some("v"),
            subfields = List(
              MarcSubfield(tag = "a", content = "Vol 1–5")
            ))
        )
      )

      getTitle(itemData) shouldBe Some("Vol 1–5")
    }

    it("picks the first suitable varfield if there are multiple options") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = Some("b"), content = Some("S11.1L")),
          VarField(fieldTag = Some("v"), content = Some("Volumes 1–5")),
          VarField(
            fieldTag = Some("v"),
            subfields = List(
              MarcSubfield(tag = "a", content = "Vol 1–5")
            ))
        )
      )

      getTitle(itemData) shouldBe Some("Volumes 1–5")
    }

    def getTitle(itemData: SierraItemData): Option[String] = {
      val transformedItem = getTransformedItems(
        itemDataMap = Map(createSierraItemNumber -> itemData)
      ).head

      transformedItem.title
    }
  }

  it("removes items with deleted=true") {
    val item1 = createSierraItemDataWith(deleted = true)
    val item2 = createSierraItemDataWith(deleted = false)

    val itemDataMap = Map(
      createSierraItemNumber -> item1,
      createSierraItemNumber -> item2
    )

    getTransformedItems(itemDataMap = itemDataMap) should have size 1
  }

  it("ignores all digital locations - 'dlnk', 'digi'") {
    val bibData = createSierraBibDataWith(
      locations = Some(
        List(
          SierraSourceLocation("digi", "Digitised Collections"),
          SierraSourceLocation("dlnk", "Digitised content")
        ))
    )

    getTransformedItems(bibData = bibData) shouldBe List()
  }

  it("creates an item with a physical location") {
    val sierraLocation = SierraSourceLocation(
      code = "sghi2",
      name = "Closed stores Hist. 2"
    )
    val itemData = createSierraItemDataWith(location = Some(sierraLocation))

    val itemDataMap = Map(createSierraItemNumber -> itemData)

    val item = getTransformedItems(itemDataMap = itemDataMap).head
    item.locations shouldBe List(
      PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label
      )
    )
  }

  it("uses the Sierra location name as the label for non-closed locations") {
    val openLocation = SierraSourceLocation(
      code = "wghib",
      name = "Biographies"
    )
    val itemData = createSierraItemDataWith(location = Some(openLocation))

    val itemDataMap = Map(createSierraItemNumber -> itemData)

    val item = getTransformedItems(itemDataMap = itemDataMap).head
    item.locations shouldBe List(
      PhysicalLocation(
        locationType = LocationType.OpenShelves,
        label = openLocation.name
      )
    )
  }

  it("creates an item with a physical location and ignores digital locations") {
    val sierraPhysicalLocation1 =
      SierraSourceLocation("sicon", "Closed stores Iconographic")

    val bibData =
      createSierraBibDataWith(
        locations = Some(
          List(
            SierraSourceLocation("digi", "Digitised Collections"),
            SierraSourceLocation("dlnk", "Digitised content"))))

    val itemDataMap = Map(
      createSierraItemNumber -> createSierraItemDataWith(
        location = Some(sierraPhysicalLocation1))
    )

    val results =
      getTransformedItems(bibData = bibData, itemDataMap = itemDataMap)

    results.head.locations should be(
      List(
        PhysicalLocation(
          locationType = LocationType.ClosedStores,
          label = LocationType.ClosedStores.label
        )
      ))
  }

  describe(
    "handling locations which are 'contained in above' or 'bound in above'") {
    it("skips adding a location if the Sierra location is 'bound in above'") {
      val itemDataMap = Map(
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("bwith", "bound in above"))
        )
      )

      val items = getTransformedItems(itemDataMap = itemDataMap)

      items should have size 1
      items.head.locations shouldBe empty
    }

    it(
      "adds a location to 'bound/contained in above' if the other locations are unambiguous") {
      val itemDataMap = Map(
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("bwith", "bound in above"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location =
            Some(SierraSourceLocation("sicon", "Closed stores Iconographic"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("cwith", "contained in above"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location =
            Some(SierraSourceLocation("sicon", "Closed stores Iconographic"))
        )
      )

      val items = getTransformedItems(itemDataMap = itemDataMap)

      items should have size 4
      items.foreach {
        _.locations shouldBe List(
          PhysicalLocation(
            locationType = LocationType.ClosedStores,
            label = LocationType.ClosedStores.label
          )
        )
      }
    }

    it(
      "adds a location to 'bound/contained in above' if the other locations are all closed") {
      val itemDataMap = Map(
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("bwith", "bound in above"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location =
            Some(SierraSourceLocation("sicon", "Closed stores Iconographic"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("cwith", "contained in above"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location =
            Some(SierraSourceLocation("sobhi", "Closed stores P.B. Hindi"))
        )
      )

      val items = getTransformedItems(itemDataMap = itemDataMap)

      items should have size 4
      items.foreach {
        _.locations shouldBe List(
          PhysicalLocation(
            locationType = LocationType.ClosedStores,
            label = LocationType.ClosedStores.label
          )
        )
      }
    }

    it(
      "skips adding a location to 'bound/contained in above' if the other locations are ambiguous") {
      val itemDataMap = Map(
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("bwith", "bound in above"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location =
            Some(SierraSourceLocation("sicon", "Closed stores Iconographic"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("cwith", "contained in above"))
        ),
        createSierraItemNumber -> createSierraItemDataWith(
          location = Some(SierraSourceLocation("info", "Open shelves"))
        )
      )

      val items = getTransformedItems(itemDataMap = itemDataMap)

      items should have size 4

      items.map { _.locations.length }.sorted shouldBe List(0, 0, 1, 1)
    }
  }

  it("sorts items by sierra-identifier") {
    val itemData = Map(
      SierraItemNumber("0000002") -> createSierraItemData,
      SierraItemNumber("0000001") -> createSierraItemData,
      SierraItemNumber("0000004") -> createSierraItemData,
      SierraItemNumber("0000003") -> createSierraItemData,
    )
    getTransformedItems(itemDataMap = itemData)
      .map(_.id.asInstanceOf[IdState.Identifiable].otherIdentifiers.head.value) shouldBe
      List(
        "0000001",
        "0000002",
        "0000003",
        "0000004"
      )
  }

  private def getTransformedItems(
    bibData: SierraBibData = createSierraBibData,
    itemDataMap: Map[SierraItemNumber, SierraItemData] = Map())
    : List[Item[IdState.Unminted]] =
    SierraItems(itemDataMap)(createSierraBibNumber, bibData)
}
