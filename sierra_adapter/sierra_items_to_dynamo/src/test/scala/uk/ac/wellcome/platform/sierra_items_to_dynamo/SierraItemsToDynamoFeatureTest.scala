package uk.ac.wellcome.platform.sierra_items_to_dynamo

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers

class SierraItemsToDynamoFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SierraAdapterHelpers
    with SierraGenerators
    with WorkerServiceFixture {

  it("reads items from Sierra and adds them to DynamoDB") {
    val dao = createDao
    val store = createItemStore

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(queue, dao, store, messageSender) { _ =>
        val itemRecord = createSierraItemRecordWith(
          bibIds = List(createSierraBibNumber)
        )

        sendNotificationToSQS(
          queue = queue,
          message = itemRecord
        )

        eventually {
          assertStoredAndSent(
            itemRecord = itemRecord,
            messageSender = messageSender,
            dao = dao,
            vhs = createItemVhs(dao, store)
          )
        }
      }
    }
  }
}
