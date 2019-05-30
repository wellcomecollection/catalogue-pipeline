package uk.ac.wellcome.platform.sierra_item_merger.services

import org.scalatest.FunSpec
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.platform.sierra_item_merger.fixtures.SierraItemMergerFixtures
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.streaming.CodecInstances._

import scala.concurrent.ExecutionContext.Implicits.global

class SierraItemMergerUpdaterServiceTest
    extends FunSpec
    with ScalaFutures
    with SQS
    with SierraAdapterHelpers
    with SierraGenerators
    with SierraItemMergerFixtures {

  it("creates a record if it receives an item with a bibId that doesn't exist") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

    val bibId = createSierraBibNumber
    val newItemRecord = createSierraItemRecordWith(
      bibIds = List(bibId)
    )

    whenReady(updaterService.update(newItemRecord)) { _ =>
      val expectedSierraTransformable =
        createSierraTransformableWith(
          sierraId = bibId,
          maybeBibRecord = None,
          itemRecords = List(newItemRecord)
        )

      assertStored(expectedSierraTransformable, vhs = vhs)
    }
  }

  it("updates multiple merged records if the item contains multiple bibIds") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

    val bibIdNotExisting = createSierraBibNumber
    val bibIdWithOldData = createSierraBibNumber
    val bibIdWithNewerData = createSierraBibNumber

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

    storeInVHS(Seq(oldTransformable, newTransformable), vhs = vhs)

    whenReady(updaterService.update(itemRecord)) { _ =>
      assertStored(expectedNewSierraTransformable, vhs = vhs)
      assertStored(expectedUpdatedSierraTransformable, vhs = vhs)
      assertStored(newTransformable, vhs = vhs)
    }
  }

  it("updates an item if it receives an update with a newer date") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

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

    storeInVHS(oldTransformable, vhs = vhs)

    val newItemRecord = itemRecord.copy(
      data = """{"data": "newer"}""",
      modifiedDate = newerDate
    )

    whenReady(updaterService.update(newItemRecord)) { _ =>
      val expectedTransformable = oldTransformable.copy(
        itemRecords = Map(itemRecord.id -> newItemRecord)
      )

      assertStored(expectedTransformable, vhs = vhs)
    }
  }

  it("unlinks an item if it is updated with an unlinked item") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

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

    storeInVHS(Seq(sierraTransformable1, sierraTransformable2), vhs = vhs)

    whenReady(updaterService.update(unlinkItemRecord)) { _ =>
      assertStored(expectedTransformable1, vhs = vhs)
      assertStored(expectedTransformable2, vhs = vhs)
    }
  }

  it("unlinks and updates a bib from a single call") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

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

    storeInVHS(Seq(sierraTransformable1, sierraTransformable2), vhs = vhs)

    whenReady(updaterService.update(unlinkItemRecord)) { _ =>
      assertStored(expectedTransformable1, vhs = vhs)
      assertStored(expectedTransformable2, vhs = vhs)
    }
  }

  it("does not unlink an item if it receives an out of date unlink update") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

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

    storeInVHS(Seq(sierraTransformable1, sierraTransformable2), vhs = vhs)

    whenReady(updaterService.update(unlinkItemRecord)) { _ =>
      assertStored(expectedTransformable1, vhs = vhs)
      assertStored(expectedTransformable2, vhs = vhs)
    }
  }

  it("does not update an item if it receives an update with an older date") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

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

    val oldItemRecord = itemRecord.copy(
      modifiedDate = olderDate
    )

    storeInVHS(transformable, vhs = vhs)

    whenReady(updaterService.update(oldItemRecord)) { _ =>
      assertStored(transformable, vhs = vhs)
    }
  }

  it("adds an item to the record if the bibId exists but has no itemData") {
    val vhs = createVhs()

    val updaterService = new SierraItemMergerUpdaterService(vhs)

    val bibId = createSierraBibNumber
    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None
    )

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId)
    )

    val expectedTransformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    storeInVHS(transformable, vhs = vhs)

    whenReady(updaterService.update(itemRecord)) { _ =>
      assertStored(expectedTransformable, vhs = vhs)
    }
  }

  it("returns a failed future if putting an item fails") {
    val exception = new Throwable("BOOM!")

    val brokenStore = new SierraStore() {
      override def put(namespace: String)(
        input: SierraTransformable,
        keyPrefix: KeyPrefix,
        keySuffix: KeySuffix,
        userMetadata: Map[String, String]): Either[WriteError, ObjectLocation] =
        Left(BackendWriteError(exception))
    }

    val vhs = createVhs(store = brokenStore)

    val updaterService = new SierraItemMergerUpdaterService(vhs)

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(createSierraBibNumber)
    )

    whenReady(updaterService.update(itemRecord).failed) {
      _ shouldBe exception
    }
  }
}
