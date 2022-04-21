package weco.catalogue.display_model.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.ItemsGenerators

class DisplayItemTest extends AnyFunSpec with Matchers with ItemsGenerators {

  it("reads an identified Item as a displayItem") {
    val item = createIdentifiedItem

    val displayItem = DisplayItem(
      item = item,
      includesIdentifiers = true
    )

    displayItem.id shouldBe Some(item.id.canonicalId.underlying)
    displayItem.locations shouldBe List(DisplayLocation(item.locations.head))
    displayItem.identifiers shouldBe Some(
      List(DisplayIdentifier(item.id.sourceIdentifier))
    )
    displayItem.ontologyType shouldBe "Item"
  }
  it("parses an unidentified Item as a displayItem") {
    val item = createUnidentifiableItem

    val displayItem = DisplayItem(
      item = item,
      includesIdentifiers = true
    )

    displayItem shouldBe DisplayItem(
      id = None,
      identifiers = None,
      locations = List(DisplayLocation(item.locations.head))
    )
  }

  it("parses an unidentified Item without any locations") {
    val item = createUnidentifiableItemWith(
      locations = List()
    )

    val displayItem = DisplayItem(
      item = item,
      includesIdentifiers = true
    )

    displayItem.locations shouldBe List()
  }

  it("parses an Item without any extra identifiers") {
    val item = createIdentifiedItem

    val displayItem = DisplayItem(
      item = item,
      includesIdentifiers = true
    )

    displayItem.identifiers shouldBe Some(
      List(DisplayIdentifier(item.id.sourceIdentifier))
    )
  }

  it("parses an identified Item without any locations") {
    val item = createIdentifiedItemWith(locations = List())

    val displayItem = DisplayItem(
      item = item,
      includesIdentifiers = true
    )

    displayItem.locations shouldBe List()
  }

  it("parses an identified Item with title") {
    val title = Some("Nice item")
    val item = createIdentifiedItemWith(title = title)

    val displayItem = DisplayItem(
      item = item,
      includesIdentifiers = true
    )

    displayItem.title shouldBe title
  }
}
