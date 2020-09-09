package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraItemNumber}

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
      .asInstanceOf[Identifiable]
      .sourceIdentifier shouldBe sourceIdentifier
  }

  it("extracts the title from item varfield $v") {
    val itemId = createSierraItemNumber
    val itemData = createSierraItemData.copy(
      varFields = List(
        VarField(fieldTag = Some("b"), content = Some("S11.1L")),
        VarField(fieldTag = Some("v"), content = Some("Envelope")),
      )
    )

    val transformedItem = getTransformedItems(
      itemDataMap = Map(itemId -> itemData)
    ).head

    transformedItem.title shouldBe Some("Envelope")
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
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      locations = Some(
        List(
          SierraSourceLocation("digi", "Digitised Collections"),
          SierraSourceLocation("dlnk", "Digitised content")
        ))
    )

    getTransformedItems(bibId = bibId, bibData = bibData) shouldBe List()
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
      PhysicalLocationDeprecated(
        locationType = LocationType(sierraLocation.code),
        label = sierraLocation.name
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
        PhysicalLocationDeprecated(
          locationType = LocationType(sierraPhysicalLocation1.code),
          label = sierraPhysicalLocation1.name
        )
      ))
  }

  it("sorts items by sierra-identifier") {
    val itemData = Map(
      SierraItemNumber("0000002") -> createSierraItemData,
      SierraItemNumber("0000001") -> createSierraItemData,
      SierraItemNumber("0000004") -> createSierraItemData,
      SierraItemNumber("0000003") -> createSierraItemData,
    )
    getTransformedItems(itemDataMap = itemData)
      .map(_.id.asInstanceOf[Identifiable].otherIdentifiers.head.value) shouldBe
      List(
        "0000001",
        "0000002",
        "0000003",
        "0000004"
      )
  }

  private def getTransformedItems(
    bibId: SierraBibNumber = createSierraBibNumber,
    bibData: SierraBibData = createSierraBibData,
    itemDataMap: Map[SierraItemNumber, SierraItemData] = Map())
    : List[Item[Unminted]] =
    SierraItems(itemDataMap)(bibId, bibData)
}
