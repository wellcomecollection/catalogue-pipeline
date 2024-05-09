package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.Item
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.{SierraBibData, SierraItemData}
import weco.sierra.models.fields.SierraLocation
import weco.sierra.models.identifiers.SierraItemNumber
import weco.sierra.models.marc.{FixedField, Subfield, VarField}

class SierraItemsTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with SierraDataGenerators {

  it("creates both forms of the Sierra ID in 'identifiers'") {
    val itemData = createSierraItemData
    val itemId = itemData.id

    val sourceIdentifier1 = createSierraSystemSourceIdentifierWith(
      ontologyType = "Item",
      value = itemId.withCheckDigit
    )

    val sourceIdentifier2 = SourceIdentifier(
      identifierType = IdentifierType.SierraIdentifier,
      ontologyType = "Item",
      value = itemId.withoutCheckDigit
    )

    val expectedIdentifiers = List(sourceIdentifier1, sourceIdentifier2)

    val transformedItem = getTransformedItems(
      itemDataEntries = Seq(itemData)
    ).head

    transformedItem.id.allSourceIdentifiers shouldBe expectedIdentifiers
  }

  it("uses the full Sierra system number as the source identifier") {
    val itemId = createSierraItemNumber
    val sourceIdentifier = createSierraSystemSourceIdentifierWith(
      ontologyType = "Item",
      value = itemId.withCheckDigit
    )

    val itemData = createSierraItemDataWith(
      id = itemId
    )

    val transformedItem = getTransformedItems(
      itemDataEntries = Seq(itemData)
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
          VarField(fieldTag = "v", content = "Envelope")
        )
      )

      getTitle(itemData) shouldBe Some("Envelope")
    }

    it("skips instances of field tag that are empty") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = "b", content = "S11.1L"),
          VarField(fieldTag = "v", content = ""),
          VarField(fieldTag = "v", content = "Envelope")
        )
      )

      getTitle(itemData) shouldBe Some("Envelope")
    }

    it("uses subfield ǂa if field tag v doesn't have a contents field") {
      val itemData = createSierraItemDataWith(
        varFields = List(
          VarField(fieldTag = "b", content = "S11.1L"),
          VarField(
            fieldTag = Some("v"),
            subfields = List(
              Subfield(tag = "a", content = "Vol 1–5")
            )
          )
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
              Subfield(tag = "a", content = "Vol 1–5")
            )
          )
        )
      )

      getTitle(itemData) shouldBe Some("Volumes 1–5")
    }

    it("uses the copy number if there are multiple items and no field tag v") {
      val itemDataEntries = Seq(1, 2, 4).map {
        copyNo =>
          createSierraItemDataWith(
            copyNo = Some(copyNo)
          )
      }

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items.map { _.title.get } should contain theSameElementsAs Seq(
        "Copy 1",
        "Copy 2",
        "Copy 4"
      )
    }

    it("omits the copy number title if there's only a single item") {
      val itemDataEntries = Seq(
        createSierraItemDataWith(copyNo = Some(1))
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items.map { _.title } shouldBe Seq(None)
    }

    it("omits the copy number title if every item has the same copy number") {
      val itemDataEntries = Seq(
        createSierraItemDataWith(copyNo = Some(1)),
        createSierraItemDataWith(copyNo = Some(1)),
        createSierraItemDataWith(copyNo = Some(1))
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items.map { _.title } shouldBe Seq(None, None, None)
    }

    it("uses the title if every item has the same explicitly set title") {
      val itemDataEntries = Seq(
        createSierraItemDataWith(
          varFields = List(
            VarField(fieldTag = "v", content = "Impression")
          )
        ),
        createSierraItemDataWith(
          varFields = List(
            VarField(fieldTag = "v", content = "Impression")
          )
        ),
        createSierraItemDataWith(
          varFields = List(
            VarField(fieldTag = "v", content = "Impression")
          )
        )
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items.map { _.title } shouldBe Seq(
        Some("Impression"),
        Some("Impression"),
        Some("Impression")
      )
    }

    def getTitle(itemData: SierraItemData): Option[String] = {
      val transformedItem = getTransformedItems(
        itemDataEntries = Seq(itemData)
      ).head

      transformedItem.title
    }
  }

  it("skips deleted items") {
    val itemDataEntries = Seq(
      createSierraItemData,
      createSierraItemData,
      createSierraItemData
    )

    // First we transform the items without deleting them, to
    // check they're not being skipped for a reason unrelated
    // to deleted=true
    getTransformedItems(itemDataEntries =
      itemDataEntries
    ) should have size itemDataEntries.size

    // Then we mark them as deleted, and check they're all ignored.
    val deleteditemDataEntries = itemDataEntries.map(_.copy(deleted = true))

    getTransformedItems(itemDataEntries =
      deleteditemDataEntries
    ) should have size 0
  }

  it("skips suppressed items") {
    val itemDataEntries = Seq(
      createSierraItemData,
      createSierraItemData,
      createSierraItemData
    )

    // First we transform the items without suppressing them, to
    // check they're not being skipped for a reason unrelated
    // to suppressing=true
    getTransformedItems(itemDataEntries =
      itemDataEntries
    ) should have size itemDataEntries.size

    // Then we mark them as deleted, and check they're all ignored.
    val suppresseditemDataEntries =
      itemDataEntries
        .map(_.copy(suppressed = true))

    getTransformedItems(itemDataEntries =
      suppresseditemDataEntries
    ) should have size 0
  }

  it("ignores all digital locations - 'dlnk', 'digi'") {
    val bibData = createSierraBibDataWith(
      locations = Some(
        List(
          SierraLocation("digi", "Digitised Collections"),
          SierraLocation("dlnk", "Digitised content")
        )
      )
    )

    getTransformedItems(bibData = bibData) shouldBe List()
  }

  it("creates an item with a physical location") {
    val sierraLocation = SierraLocation(
      code = "sghi2",
      name = "Closed stores Hist. 2"
    )
    val itemData = createSierraItemDataWith(
      location = Some(sierraLocation),
      fixedFields = Map(
        "79" -> FixedField(
          label = "LOCATION",
          value = "sghi2",
          display = "Closed stores Hist. 2"
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

    val itemDataEntries = Seq(itemData)

    val item = getTransformedItems(itemDataEntries = itemDataEntries).head
    item.locations shouldBe List(
      PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label,
        accessConditions = List(
          AccessCondition(
            method = AccessMethod.OnlineRequest,
            status = AccessStatus.Open
          )
        )
      )
    )
  }

  it("adds a note to the item") {
    val sierraLocation = SierraLocation(
      code = "scmac",
      name = "Closed stores Arch. & MSS"
    )
    val itemData = createSierraItemDataWith(
      location = Some(sierraLocation),
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
      varFields = List(
        VarField(
          fieldTag = "n",
          content = "uncoloured impression"
        )
      )
    )

    val itemDataEntries = Seq(itemData)

    val item = getTransformedItems(itemDataEntries = itemDataEntries).head
    item.note shouldBe Some("uncoloured impression")
  }

  it("uses the Sierra location name as the label for non-closed locations") {
    val openLocation = SierraLocation(
      code = "wghib",
      name = "Biographies"
    )

    val itemDataEntries = Seq(
      createSierraItemDataWith(location = Some(openLocation))
    )

    val item = getTransformedItems(itemDataEntries = itemDataEntries).head
    item.locations should have size 1
    item.locations.head
      .asInstanceOf[PhysicalLocation]
      .label shouldBe openLocation.name
  }

  it("creates an item with a physical location and ignores digital locations") {
    val sierraPhysicalLocation1 =
      SierraLocation("sicon", "Closed stores Iconographic")

    val bibData =
      createSierraBibDataWith(
        locations = Some(
          List(
            SierraLocation("digi", "Digitised Collections"),
            SierraLocation("dlnk", "Digitised content")
          )
        )
      )

    val itemData = createSierraItemDataWith(
      location = Some(sierraPhysicalLocation1),
      fixedFields = Map(
        "79" -> FixedField(
          label = "LOCATION",
          value = "sicon",
          display = "Closed stores Iconographic"
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

    val results =
      getTransformedItems(bibData = bibData, itemDataEntries = Seq(itemData))

    results.head.locations should be(
      List(
        PhysicalLocation(
          locationType = LocationType.ClosedStores,
          label = LocationType.ClosedStores.label,
          accessConditions = List(
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Open
            )
          )
        )
      )
    )
  }

  describe(
    "handling locations which are 'contained in above' or 'bound in above'"
  ) {
    it("skips adding a location if the Sierra location is 'bound in above'") {
      val itemDataEntries = Seq(
        createSierraItemDataWith(
          location = Some(SierraLocation("bwith", "bound in above"))
        )
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items should have size 1
      items.head.locations should have size 0
    }

    it(
      "adds a location to 'bound/contained in above' if the other locations are unambiguous"
    ) {
      val itemDataEntries = Seq(
        createSierraItemDataWith(
          location = Some(SierraLocation("bwith", "bound in above"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("sicon", "Closed stores Iconographic"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("cwith", "contained in above"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("sicon", "Closed stores Iconographic"))
        )
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items should have size 4
      items.foreach {
        _.locations.foreach {
          loc =>
            val physicalLoc = loc.asInstanceOf[PhysicalLocation]
            physicalLoc.locationType shouldBe LocationType.ClosedStores
            physicalLoc.label shouldBe LocationType.ClosedStores.label
        }
      }
    }

    it(
      "adds a location to 'bound/contained in above' if the other locations are all closed"
    ) {
      val itemDataEntries = Seq(
        createSierraItemDataWith(
          location = Some(SierraLocation("bwith", "bound in above"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("sicon", "Closed stores Iconographic"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("cwith", "contained in above"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("sobhi", "Closed stores P.B. Hindi"))
        )
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items should have size 4
      items.foreach {
        _.locations.foreach {
          loc =>
            val physicalLoc = loc.asInstanceOf[PhysicalLocation]
            physicalLoc.locationType shouldBe LocationType.ClosedStores
            physicalLoc.label shouldBe LocationType.ClosedStores.label
        }
      }
    }

    it(
      "skips adding a location to 'bound/contained in above' if the other locations are ambiguous"
    ) {
      val itemDataEntries = Seq(
        createSierraItemDataWith(
          location = Some(SierraLocation("bwith", "bound in above"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("sicon", "Closed stores Iconographic"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("cwith", "contained in above"))
        ),
        createSierraItemDataWith(
          location = Some(SierraLocation("info", "Open shelves"))
        )
      )

      val items = getTransformedItems(itemDataEntries = itemDataEntries)

      items should have size 4

      items.map { _.locations.length }.sorted shouldBe List(0, 0, 1, 1)
    }
  }

  it("sorts items by sierra-identifier") {
    val itemData = Seq(
      createSierraItemDataWith(id = SierraItemNumber("0000002")),
      createSierraItemDataWith(id = SierraItemNumber("0000001")),
      createSierraItemDataWith(id = SierraItemNumber("0000004")),
      createSierraItemDataWith(id = SierraItemNumber("0000003"))
    )
    getTransformedItems(itemDataEntries = itemData)
      .map(
        _.id.asInstanceOf[IdState.Identifiable].otherIdentifiers.head.value
      ) shouldBe
      List(
        "0000001",
        "0000002",
        "0000003",
        "0000004"
      )
  }

  private def getTransformedItems(
    bibData: SierraBibData = createSierraBibData,
    itemDataEntries: Seq[SierraItemData] = Seq()
  ): List[Item[IdState.Unminted]] =
    SierraItems(
      bibId = createSierraBibNumber,
      bibData = bibData,
      itemDataEntries = itemDataEntries
    )
}
