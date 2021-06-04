package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.marc.{
  FixedField,
  MarcSubfield,
  VarField
}
import weco.catalogue.source_model.sierra.source.SierraSourceLocation
import weco.catalogue.source_model.sierra.{
  SierraBibData,
  SierraItemData,
  SierraItemNumber
}

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
      identifierType = IdentifierType.SierraIdentifier,
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
          VarField(fieldTag = "b", content = "S11.1L"),
          VarField(fieldTag = "v", content = "Envelope"),
        )
      )

      getTitle(itemData) shouldBe Some("Envelope")
    }

    it("skips instances of field tag that are empty") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = "b", content = "S11.1L"),
          VarField(fieldTag = "v", content = ""),
          VarField(fieldTag = "v", content = "Envelope"),
        )
      )

      getTitle(itemData) shouldBe Some("Envelope")
    }

    it(
      "uses the contents of subfield ǂa if field tag ǂv doesn't have a contents field") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = "b", content = "S11.1L"),
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
          VarField(fieldTag = "b", content = "S11.1L"),
          VarField(fieldTag = "v", content = "Volumes 1–5"),
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

  it("skips deleted items") {
    val itemDataMap = (1 to 3).map { _ =>
      createSierraItemNumber -> createSierraItemData
    }.toMap

    // First we transform the items without deleting them, to
    // check they're not being skipped for a reason unrelated
    // to deleted=true
    getTransformedItems(itemDataMap = itemDataMap) should have size itemDataMap.size

    // Then we mark them as deleted, and check they're all ignored.
    val deletedItemDataMap =
      itemDataMap
        .map { case (id, itemData) => id -> itemData.copy(deleted = true) }

    getTransformedItems(itemDataMap = deletedItemDataMap) shouldBe empty
  }

  it("skips suppressed items") {
    val itemDataMap = (1 to 3).map { _ =>
      createSierraItemNumber -> createSierraItemData
    }.toMap

    // First we transform the items without suppressing them, to
    // check they're not being skipped for a reason unrelated
    // to suppressing=true
    getTransformedItems(itemDataMap = itemDataMap) should have size itemDataMap.size

    // Then we mark them as deleted, and check they're all ignored.
    val suppressedItemDataMap =
      itemDataMap
        .map { case (id, itemData) => id -> itemData.copy(suppressed = true) }

    getTransformedItems(itemDataMap = suppressedItemDataMap) shouldBe empty
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
    val itemData = createSierraItemDataWith(
      location = Some(sierraLocation),
      fixedFields = Map(
        "79" -> FixedField(
          label = "LOCATION",
          value = "sghi2",
          display = "Closed stores Hist. 2"),
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

    val itemDataMap = Map(createSierraItemNumber -> itemData)

    val item = getTransformedItems(itemDataMap = itemDataMap).head
    item.locations shouldBe List(
      PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label,
        accessConditions =
          List(AccessCondition(method = Some(AccessMethod.OnlineRequest)))
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
    item.locations should have size 1
    item.locations.head
      .asInstanceOf[PhysicalLocation]
      .label shouldBe openLocation.name
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

    val itemData = createSierraItemDataWith(
      location = Some(sierraPhysicalLocation1),
      fixedFields = Map(
        "79" -> FixedField(
          label = "LOCATION",
          value = "sicon",
          display = "Closed stores Iconographic"),
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

    val results =
      getTransformedItems(
        bibData = bibData,
        itemDataMap = Map(createSierraItemNumber -> itemData))

    results.head.locations should be(
      List(
        PhysicalLocation(
          locationType = LocationType.ClosedStores,
          label = LocationType.ClosedStores.label,
          accessConditions =
            List(AccessCondition(method = Some(AccessMethod.OnlineRequest)))
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
        _.locations.foreach { loc =>
          val physicalLoc = loc.asInstanceOf[PhysicalLocation]
          physicalLoc.locationType shouldBe LocationType.ClosedStores
          physicalLoc.label shouldBe LocationType.ClosedStores.label
        }
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
        _.locations.foreach { loc =>
          val physicalLoc = loc.asInstanceOf[PhysicalLocation]
          physicalLoc.locationType shouldBe LocationType.ClosedStores
          physicalLoc.label shouldBe LocationType.ClosedStores.label
        }
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
    SierraItems(createSierraBibNumber, bibData, itemDataMap)
}
