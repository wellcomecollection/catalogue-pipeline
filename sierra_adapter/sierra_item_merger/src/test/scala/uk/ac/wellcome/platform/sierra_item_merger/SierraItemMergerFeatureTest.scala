package uk.ac.wellcome.platform.sierra_item_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.sierra_item_merger.fixtures.SierraItemMergerFixtures
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraItemRecord,
  SierraTransformable
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version

class SierraItemMergerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SQS
    with SierraGenerators
    with SierraAdapterHelpers
    with SierraItemMergerFixtures {

  it("stores an item from SQS") {
    withLocalSqsQueue() { queue =>
      val bibId = createSierraBibNumber
      val itemRecord = createSierraItemRecordWith(
        bibIds = List(bibId)
      )
      val key = Version(itemRecord.id.withoutCheckDigit, 0)
      val itemRecordStore =
        createStore[SierraItemRecord](Map(key -> itemRecord))
      val sierraTransformableStore = createStore[SierraTransformable]()
      withSierraWorkerService(queue, itemRecordStore, sierraTransformableStore) {
        case (service, messageSender) =>
          service.run()

          sendNotificationToSQS(queue = queue, key)

          val expectedSierraTransformable =
            createSierraTransformableWith(
              sierraId = bibId,
              maybeBibRecord = None,
              itemRecords = List(itemRecord)
            )

          eventually {
            assertStoredAndSent(
              Version(
                expectedSierraTransformable.sierraId.withoutCheckDigit,
                0),
              expectedSierraTransformable,
              sierraTransformableStore,
              messageSender
            )
          }
      }
    }
  }

  it("drops the message if the version in the items VHS has advanced") {
    withLocalSqsQueue() { queue =>
      val bibId = createSierraBibNumber
      val itemRecord = createSierraItemRecordWith(
        bibIds = List(bibId)
      )
      val id = itemRecord.id.withoutCheckDigit
      val key = Version(id, 0)
      val itemRecordStore =
        createStore[SierraItemRecord](Map(key.copy(version = 1) -> itemRecord))
      val sierraTransformableStore = createStore[SierraTransformable]()
      withSierraWorkerService(queue, itemRecordStore, sierraTransformableStore) {
        case (service, messageSender) =>
          service.run()

          sendNotificationToSQS(queue = queue, key)

          Thread.sleep(500)

          eventually {
            itemRecordStore.getLatest(id).right.get.id.version shouldBe 1
            messageSender.getMessages[Version[String, Int]] shouldBe empty
          }
      }
    }
  }

  it("stores multiple items from SQS") {
    withLocalSqsQueue() { queue =>
      val bibId1 = createSierraBibNumber
      val itemRecord1 = createSierraItemRecordWith(
        bibIds = List(bibId1)
      )
      val key1 = Version(itemRecord1.id.withoutCheckDigit, 0)

      val bibId2 = createSierraBibNumber
      val itemRecord2 = createSierraItemRecordWith(
        bibIds = List(bibId2)
      )
      val key2 = Version(itemRecord2.id.withoutCheckDigit, 0)
      val itemRecordStore = createStore[SierraItemRecord](
        Map(key1 -> itemRecord1, key2 -> itemRecord2))
      val sierraTransformableStore = createStore[SierraTransformable]()
      withSierraWorkerService(queue, itemRecordStore, sierraTransformableStore) {
        case (service, messageSender) =>
          service.run()
          sendNotificationToSQS(queue, key1)
          sendNotificationToSQS(queue, key2)

          eventually {
            val expectedSierraTransformable1 =
              createSierraTransformableWith(
                sierraId = bibId1,
                maybeBibRecord = None,
                itemRecords = List(itemRecord1)
              )

            val expectedSierraTransformable2 =
              createSierraTransformableWith(
                sierraId = bibId2,
                maybeBibRecord = None,
                itemRecords = List(itemRecord2)
              )

            assertStoredAndSent(
              Version(bibId1.withoutCheckDigit, 0),
              expectedSierraTransformable1,
              sierraTransformableStore,
              messageSender
            )
            assertStoredAndSent(
              Version(bibId2.withoutCheckDigit, 0),
              expectedSierraTransformable2,
              sierraTransformableStore,
              messageSender
            )
          }
      }
    }
  }

  it("sends a notification for every transformable which changes") {
    withLocalSqsQueue() { queue =>
      val bibIds = createSierraBibNumbers(3)
      val itemRecord = createSierraItemRecordWith(
        bibIds = bibIds
      )
      val key = Version(itemRecord.id.withoutCheckDigit, 0)
      val itemRecordStore =
        createStore[SierraItemRecord](Map(key -> itemRecord))
      val sierraTransformableStore = createStore[SierraTransformable]()
      withSierraWorkerService(queue, itemRecordStore, sierraTransformableStore) {
        case (service, messageSender) =>
          service.run()

          sendNotificationToSQS(queue = queue, key)

          val expectedTransformables = bibIds.map { bibId =>
            createSierraTransformableWith(
              sierraId = bibId,
              maybeBibRecord = None,
              itemRecords = List(itemRecord)
            )
          }

          eventually {
            expectedTransformables.map { tranformable =>
              assertStoredAndSent(
                Version(tranformable.sierraId.withoutCheckDigit, 0),
                tranformable,
                sierraTransformableStore,
                messageSender
              )
            }
          }
      }
    }
  }

}
