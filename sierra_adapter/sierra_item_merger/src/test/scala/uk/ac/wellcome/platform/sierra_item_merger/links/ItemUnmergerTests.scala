package uk.ac.wellcome.platform.sierra_item_merger.links

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.sierra_adapter.model.SierraGenerators

class ItemUnmergerTests extends AnyFunSpec with Matchers with SierraGenerators {

  it("removes the item if it already exists") {
    val bibId = createSierraBibNumber

    val record = createSierraItemRecordWith(
      bibIds = List(bibId),
      unlinkedBibIds = List()
    )

    val unlinkedItemRecord = createSierraItemRecordWith(
      id = record.id,
      bibIds = List(),
      modifiedDate = record.modifiedDate.plusSeconds(1),
      unlinkedBibIds = List(bibId)
    )

    val sierraTransformable = createSierraTransformableWith(
      sierraId = bibId,
      itemRecords = List(record)
    )

    val expectedSierraTransformable = sierraTransformable.copy(
      itemRecords = Map.empty
    )

    ItemUnmerger
      .unmergeItemRecord(sierraTransformable, unlinkedItemRecord)
      .get shouldBe expectedSierraTransformable
  }

  it("returns None when merging an unlinked record which is already absent") {
    val bibId = createSierraBibNumber

    val record = createSierraItemRecordWith(
      bibIds = List(bibId),
      unlinkedBibIds = List()
    )

    val previouslyUnlinkedRecord = createSierraItemRecordWith(
      bibIds = List(),
      unlinkedBibIds = List(bibId)
    )

    val sierraTransformable = createSierraTransformableWith(
      sierraId = bibId,
      itemRecords = List(record)
    )

    ItemUnmerger.unmergeItemRecord(sierraTransformable, previouslyUnlinkedRecord) shouldBe None
  }

  it(
    "returns None when merging an unlinked record which has linked more recently") {
    val bibId = createSierraBibNumber

    val record = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(bibId),
      unlinkedBibIds = List()
    )

    val outOfDateUnlinkedRecord = record.copy(
      modifiedDate = olderDate,
      bibIds = List(),
      unlinkedBibIds = List(bibId)
    )

    val sierraTransformable = createSierraTransformableWith(
      sierraId = bibId,
      itemRecords = List(record)
    )

    ItemUnmerger.unmergeItemRecord(sierraTransformable, outOfDateUnlinkedRecord) shouldBe None
  }

  it("only unlinks item records with matching bib IDs") {
    val bibId = createSierraBibNumber
    val unrelatedBibId = createSierraBibNumber

    val record = createSierraItemRecordWith(
      bibIds = List(bibId),
      unlinkedBibIds = List()
    )

    val unrelatedItemRecord = createSierraItemRecordWith(
      bibIds = List(),
      unlinkedBibIds = List(unrelatedBibId)
    )

    val sierraTransformable = createSierraTransformableWith(
      sierraId = bibId,
      itemRecords = List(record)
    )

    val caught = intercept[RuntimeException] {
      ItemUnmerger.unmergeItemRecord(sierraTransformable, unrelatedItemRecord)
    }

    caught.getMessage shouldEqual s"Non-matching bib id $bibId in item unlink bibs List($unrelatedBibId)"
  }
}
