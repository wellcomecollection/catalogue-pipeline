package uk.ac.wellcome.platform.sierra_item_merger.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.platform.sierra_item_merger.fixtures.SierraItemMergerFixtures
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.{UpdateNotApplied, Version}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

class SierraItemMergerUpdaterServiceTest
    extends AnyFunSpec
    with SQS
    with SierraAdapterHelpers
    with SierraGenerators
    with SierraItemMergerFixtures
    with MockitoSugar {

  it("creates a record if it receives an item with a bibId that doesn't exist") {
    val sierraTransformableStore = createStore[SierraTransformable]()

    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val bibId = createSierraBibNumber
      val newItemRecord = createSierraItemRecordWith(
        bibIds = List(bibId)
      )

      val result = sierraUpdaterService.update(newItemRecord)

      result shouldBe a[Right[_, _]]
      result.right.get shouldBe List(Version(bibId.withoutCheckDigit, 0))

      val expectedSierraTransformable =
        createSierraTransformableWith(
          sierraId = bibId,
          maybeBibRecord = None,
          itemRecords = List(newItemRecord)
        )

      assertStored(
        bibId.withoutCheckDigit,
        expectedSierraTransformable,
        sierraTransformableStore
      )
    }
  }

  it("updates multiple merged records if the item contains multiple bibIds") {
    val bibIdNotExisting = createSierraBibNumber
    val bibIdWithNewerData = createSierraBibNumber
    val bibIdWithOldData = createSierraBibNumber
    val bibIds = List(
      bibIdNotExisting,
      bibIdWithOldData,
      bibIdWithNewerData
    )

    val itemRecord = createSierraItemRecordWith(
      bibIds = bibIds
    )

    val otherItemRecord = createSierraItemRecordWith(
      bibIds = bibIds
    )
    val oldTransformable = createSierraTransformableWith(
      sierraId = bibIdWithOldData,
      maybeBibRecord = None,
      itemRecords = List(itemRecord, otherItemRecord)
    )
    val anotherItemRecord = createSierraItemRecordWith(
      bibIds = bibIds
    )

    val newTransformable = createSierraTransformableWith(
      sierraId = bibIdWithNewerData,
      maybeBibRecord = None,
      itemRecords = List(itemRecord, anotherItemRecord)
    )

    val sierraTransformableStore = createStore[SierraTransformable](
      Map(
        Version(bibIdWithOldData.withoutCheckDigit, 0) -> oldTransformable,
        Version(bibIdWithNewerData.withoutCheckDigit, 0) -> newTransformable))

    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val expectedNewSierraTransformable =
        createSierraTransformableWith(
          sierraId = bibIdNotExisting,
          maybeBibRecord = None,
          itemRecords = List(itemRecord)
        )

      val expectedUpdatedSierraTransformable =
        createSierraTransformableWith(
          sierraId = oldTransformable.sierraId,
          maybeBibRecord = None,
          itemRecords = List(itemRecord, otherItemRecord)
        )

      val result = sierraUpdaterService.update(itemRecord)

      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibIdNotExisting.withoutCheckDigit, 0),
        Version(bibIdWithOldData.withoutCheckDigit, 1),
        Version(bibIdWithNewerData.withoutCheckDigit, 1)))

      assertStored(
        expectedNewSierraTransformable.sierraId.withoutCheckDigit,
        expectedNewSierraTransformable,
        sierraTransformableStore
      )
      assertStored(
        expectedUpdatedSierraTransformable.sierraId.withoutCheckDigit,
        expectedUpdatedSierraTransformable,
        sierraTransformableStore
      )
      assertStored(
        newTransformable.sierraId.withoutCheckDigit,
        newTransformable,
        sierraTransformableStore
      )
    }
  }

  it("updates an item if it receives an update with a newer date") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibId)
    )

    val oldTransformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )
    val sierraTransformableStore = createStore[SierraTransformable](
      Map(Version(bibId.withoutCheckDigit, 0) -> oldTransformable))
    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val newItemRecord = itemRecord.copy(
        data = """{"data": "newer"}""",
        modifiedDate = newerDate
      )
      val expectedTransformable = oldTransformable.copy(
        itemRecords = Map(itemRecord.id -> newItemRecord)
      )

      val result = sierraUpdaterService.update(newItemRecord)

      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibId.withoutCheckDigit, 1)))

      assertStored(
        expectedTransformable.sierraId.withoutCheckDigit,
        expectedTransformable,
        sierraTransformableStore
      )
    }
  }

  it("unlinks an item if it is updated with an unlinked item") {
    val bibId1 = createSierraBibNumber
    val bibId2 = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 = createSierraTransformableWith(
      sierraId = bibId1,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableWith(
      sierraId = bibId2,
      maybeBibRecord = None
    )
    val sierraTransformableStore = createStore[SierraTransformable](
      Map(
        Version(bibId1.withoutCheckDigit, 0) -> sierraTransformable1,
        Version(bibId2.withoutCheckDigit, 0) -> sierraTransformable2))

    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val unlinkItemRecord = itemRecord.copy(
        bibIds = List(bibId2),
        unlinkedBibIds = List(bibId1),
        modifiedDate = itemRecord.modifiedDate.plusSeconds(1)
      )

      val expectedTransformable1 = sierraTransformable1.copy(
        itemRecords = Map.empty
      )

      val expectedItemRecords = Map(
        itemRecord.id -> itemRecord.copy(
          bibIds = List(bibId2),
          unlinkedBibIds = List(bibId1),
          modifiedDate = unlinkItemRecord.modifiedDate
        )
      )
      val expectedTransformable2 = sierraTransformable2.copy(
        itemRecords = expectedItemRecords
      )

      val result = sierraUpdaterService.update(unlinkItemRecord)

      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibId1.withoutCheckDigit, 1),
        Version(bibId2.withoutCheckDigit, 1)))

      assertStored(
        bibId1.withoutCheckDigit,
        expectedTransformable1,
        sierraTransformableStore
      )
      assertStored(
        bibId2.withoutCheckDigit,
        expectedTransformable2,
        sierraTransformableStore
      )
    }
  }

  it("unlinks and updates a bib from a single call") {
    val bibId1 = createSierraBibNumber
    val bibId2 = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 = createSierraTransformableWith(
      sierraId = bibId1,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableWith(
      sierraId = bibId2,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )
    val sierraTransformableStore = createStore[SierraTransformable](
      Map(
        Version(bibId1.withoutCheckDigit, 0) -> sierraTransformable1,
        Version(bibId2.withoutCheckDigit, 0) -> sierraTransformable2))

    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val unlinkItemRecord = itemRecord.copy(
        bibIds = List(bibId2),
        unlinkedBibIds = List(bibId1),
        modifiedDate = itemRecord.modifiedDate.plusSeconds(1)
      )

      val expectedItemData = Map(
        itemRecord.id -> unlinkItemRecord
      )

      val expectedTransformable1 = sierraTransformable1.copy(
        itemRecords = Map.empty
      )

      // In this situation the item was already linked to sierraTransformable2
      // but the modified date is updated in line with the item update
      val expectedTransformable2 = sierraTransformable2.copy(
        itemRecords = expectedItemData
      )

      val result = sierraUpdaterService.update(unlinkItemRecord)
      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibId1.withoutCheckDigit, 1),
        Version(bibId2.withoutCheckDigit, 1)))
      assertStored(
        bibId1.withoutCheckDigit,
        expectedTransformable1,
        sierraTransformableStore
      )
      assertStored(
        bibId2.withoutCheckDigit,
        expectedTransformable2,
        sierraTransformableStore
      )
    }
  }

  it("does not unlink an item if it receives an out of date unlink update") {
    val bibId1 = createSierraBibNumber
    val bibId2 = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 = createSierraTransformableWith(
      sierraId = bibId1,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableWith(
      sierraId = bibId2,
      maybeBibRecord = None
    )
    val sierraTransformableStore = createStore[SierraTransformable](
      Map(
        Version(bibId1.withoutCheckDigit, 0) -> sierraTransformable1,
        Version(bibId2.withoutCheckDigit, 0) -> sierraTransformable2))
    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val unlinkItemRecord = itemRecord.copy(
        bibIds = List(bibId2),
        unlinkedBibIds = List(bibId1),
        modifiedDate = itemRecord.modifiedDate.minusSeconds(1)
      )

      val expectedItemRecords = Map(
        itemRecord.id -> itemRecord.copy(
          bibIds = List(bibId2),
          unlinkedBibIds = List(bibId1),
          modifiedDate = unlinkItemRecord.modifiedDate
        )
      )

      // In this situation the item will _not_ be unlinked from the original
      // record but will be linked to the new record (as this is the first
      // time we've seen the link so it is valid for that bib.
      val expectedTransformable1 = sierraTransformable1
      val expectedTransformable2 = sierraTransformable2.copy(
        itemRecords = expectedItemRecords
      )

      val result = sierraUpdaterService.update(unlinkItemRecord)
      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibId1.withoutCheckDigit, 1),
        Version(bibId2.withoutCheckDigit, 1)))

      assertStored(
        bibId1.withoutCheckDigit,
        expectedTransformable1,
        sierraTransformableStore
      )
      assertStored(
        bibId2.withoutCheckDigit,
        expectedTransformable2,
        sierraTransformableStore
      )
    }
  }

  it("does not update an item if it receives an update with an older date") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(bibId)
    )

    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )
    val sierraTransformableStore = createStore[SierraTransformable](
      Map(Version(bibId.withoutCheckDigit, 0) -> transformable))
    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val oldItemRecord = itemRecord.copy(
        modifiedDate = olderDate
      )

      val result = sierraUpdaterService.update(oldItemRecord)
      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibId.withoutCheckDigit, 1)))

      assertStored(
        bibId.withoutCheckDigit,
        transformable,
        sierraTransformableStore
      )
    }
  }

  it("adds an item to the record if the bibId exists but has no itemData") {
    val bibId = createSierraBibNumber
    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None
    )
    val sierraTransformableStore = createStore[SierraTransformable](
      Map(Version(bibId.withoutCheckDigit, 0) -> transformable))
    withSierraUpdaterService(sierraTransformableStore) { sierraUpdaterService =>
      val itemRecord = createSierraItemRecordWith(
        bibIds = List(bibId)
      )

      val expectedTransformable = createSierraTransformableWith(
        sierraId = bibId,
        maybeBibRecord = None,
        itemRecords = List(itemRecord)
      )

      val result = sierraUpdaterService.update(itemRecord)
      result shouldBe a[Right[_, _]]
      result.right.get should contain theSameElementsAs (List(
        Version(bibId.withoutCheckDigit, 1)))
      assertStored(
        bibId.withoutCheckDigit,
        expectedTransformable,
        sierraTransformableStore
      )
    }
  }

  it("returns a failed future if merging an item fails") {
    val exception = new RuntimeException("AAAAARGH!")
    val failingStore = new MemoryVersionedStore(
      new MemoryStore(Map[Version[String, Int], SierraTransformable]())
      with MemoryMaxima[String, SierraTransformable]) {
      override def upsert(id: String)(t: SierraTransformable)(
        f: UpdateFunction): UpdateEither = {
        Left(UpdateNotApplied(exception))
      }
    }
    withSierraUpdaterService(failingStore) { brokenService =>
      val itemRecord = createSierraItemRecordWith(
        bibIds = List(createSierraBibNumber)
      )
      brokenService.update(itemRecord).left.get.e shouldBe exception
    }
  }

  it("returns a failed future if unlinking an item fails") {
    val exception = new RuntimeException("AAAAARGH!")
    val failingStore = new MemoryVersionedStore(
      new MemoryStore(Map[Version[String, Int], SierraTransformable]())
      with MemoryMaxima[String, SierraTransformable]) {
      override def update(id: String)(f: UpdateFunction): UpdateEither = {
        Left(UpdateNotApplied(exception))
      }
    }
    withSierraUpdaterService(failingStore) { brokenService =>
      val itemRecord = createSierraItemRecordWith(
        bibIds = List(createSierraBibNumber)
      )
      brokenService.update(itemRecord).left.get.e shouldBe exception
    }
  }
}
