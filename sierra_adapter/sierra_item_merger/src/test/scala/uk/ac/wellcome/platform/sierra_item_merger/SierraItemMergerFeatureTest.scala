package uk.ac.wellcome.platform.sierra_item_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.platform.sierra_item_merger.fixtures.SierraItemMergerFixtures
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version
import weco.catalogue.source_model.fixtures.SourceVHSFixture

class SierraItemMergerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SQS
    with SierraGenerators
    with SierraAdapterHelpers
    with SierraItemMergerFixtures
    with SourceVHSFixture {

  it("stores an item from SQS") {
    withLocalSqsQueue() { queue =>
      val bibId = createSierraBibNumber
      val itemRecord = createSierraItemRecordWith(
        bibIds = List(bibId)
      )

      val sourceVHS = createSourceVHS[SierraTransformable]

      withSierraWorkerService(queue, sourceVHS) {
        case (service, messageSender) =>
          service.run()

          sendNotificationToSQS(queue = queue, itemRecord)

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
              sourceVHS,
              messageSender
            )
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

      val bibId2 = createSierraBibNumber
      val itemRecord2 = createSierraItemRecordWith(
        bibIds = List(bibId2)
      )

      val sourceVHS = createSourceVHS[SierraTransformable]

      withSierraWorkerService(queue, sourceVHS) {
        case (service, messageSender) =>
          service.run()
          sendNotificationToSQS(queue, itemRecord1)
          sendNotificationToSQS(queue, itemRecord2)

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
              sourceVHS,
              messageSender
            )
            assertStoredAndSent(
              Version(bibId2.withoutCheckDigit, 0),
              expectedSierraTransformable2,
              sourceVHS,
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

      val sourceVHS = createSourceVHS[SierraTransformable]

      withSierraWorkerService(queue, sourceVHS) {
        case (service, messageSender) =>
          service.run()

          sendNotificationToSQS(queue = queue, itemRecord)

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
                sourceVHS,
                messageSender
              )
            }
          }
      }
    }
  }

}
