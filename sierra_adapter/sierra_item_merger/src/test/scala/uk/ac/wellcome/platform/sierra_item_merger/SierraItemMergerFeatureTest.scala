package uk.ac.wellcome.platform.sierra_item_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.platform.sierra_item_merger.fixtures.SierraItemMergerFixtures
import uk.ac.wellcome.storage.vhs.EmptyMetadata

class SierraItemMergerFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SierraGenerators
    with SierraItemMergerFixtures {

  def putItem(itemVhs: SierraItemVHS, itemRecord: SierraItemRecord): itemVhs.VHSEntry =
    itemVhs.update(id = itemRecord.id.toString)(
      (itemRecord, EmptyMetadata())
    )(
      (_, _) => (itemRecord, EmptyMetadata())
    ).right.value

  it("stores an item from SQS") {
    val dao = createDao
    val vhs = createVhs(dao = dao)
    val messageSender = new MemoryMessageSender()

    val itemDao = createDao
    val itemStore = createItemStore
    val itemVhs = createItemVhs(itemDao, itemStore)

    withLocalSqsQueue { queue =>
      withSierraWorkerService(queue, messageSender, itemStore, vhs) {
        service =>
          service.run()

          val bibId = createSierraBibNumber

          val itemRecord = createSierraItemRecordWith(
            bibIds = List(bibId)
          )

          val entry = putItem(itemVhs, itemRecord = itemRecord)
          val notification = createNotificationMessageWith(entry)

          sendSqsMessage(queue = queue, notification)

          val expectedSierraTransformable =
            createSierraTransformableWith(
              sierraId = bibId,
              maybeBibRecord = None,
              itemRecords = List(itemRecord)
            )

          eventually {
            assertStoredAndSent(
              transformable = expectedSierraTransformable,
              messageSender = messageSender,
              dao = dao,
              vhs = vhs
            )
          }
      }
    }
  }

  it("stores multiple items from SQS") {
    val dao = createDao
    val vhs = createVhs(dao = dao)
    val messageSender = new MemoryMessageSender()

    val itemDao = createDao
    val itemStore = createItemStore
    val itemVhs = createItemVhs(itemDao, itemStore)

    withLocalSqsQueue { queue =>
      withSierraWorkerService(queue, messageSender, itemStore, vhs) {
        service =>
          service.run()

          val bibId1 = createSierraBibNumber
          val itemRecord1 = createSierraItemRecordWith(
            bibIds = List(bibId1)
          )

          val entry1 = putItem(itemVhs, itemRecord = itemRecord1)
          val notification1 = createNotificationMessageWith(entry1)

          sendSqsMessage(queue = queue, notification1)

          val bibId2 = createSierraBibNumber
          val itemRecord2 = createSierraItemRecordWith(
            bibIds = List(bibId2)
          )

          val entry2 = putItem(itemVhs, itemRecord = itemRecord2)
          val notification2 = createNotificationMessageWith(entry2)

          sendSqsMessage(queue = queue, notification2)

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
              transformable = expectedSierraTransformable1,
              messageSender = messageSender,
              dao = dao,
              vhs = vhs
            )
            assertStoredAndSent(
              transformable = expectedSierraTransformable2,
              messageSender = messageSender,
              dao = dao,
              vhs = vhs
            )
          }
      }
    }
  }

  it("sends a notification for every transformable which changes") {
    val dao = createDao
    val vhs = createVhs(dao = dao)
    val messageSender = new MemoryMessageSender()

    val itemDao = createDao
    val itemStore = createItemStore
    val itemVhs = createItemVhs(itemDao, itemStore)

    withLocalSqsQueue { queue =>
      withSierraWorkerService(queue, messageSender, itemStore, vhs) {
        service =>
          service.run()

          val bibIds = createSierraBibNumbers(3)
          val itemRecord = createSierraItemRecordWith(
            bibIds = bibIds
          )

          val entry = putItem(itemVhs, itemRecord = itemRecord)
          val notification = createNotificationMessageWith(entry)

          sendSqsMessage(queue = queue, notification)

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
                transformable = tranformable,
                messageSender = messageSender,
                dao = dao,
                vhs = vhs
              )
            }
          }
      }
    }
  }
}
