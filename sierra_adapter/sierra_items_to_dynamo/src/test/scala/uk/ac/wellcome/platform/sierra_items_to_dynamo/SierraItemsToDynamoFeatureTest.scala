package uk.ac.wellcome.platform.sierra_items_to_dynamo

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraItemRecord}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers

class SierraItemsToDynamoFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SierraAdapterHelpers
    with SierraGenerators
    with WorkerServiceFixture {

  it("reads items from Sierra and adds them to DynamoDB") {
    val messageSender = new MemoryMessageSender

    withLocalSqsQueue() { queue =>
      withWorkerService(queue, messageSender = messageSender) { _ =>
          val itemRecord = createSierraItemRecordWith(
            bibIds = List(createSierraBibNumber)
          )

          sendNotificationToSQS(
            queue = queue,
            message = itemRecord
          )

          eventually {
            messageSender.getMessages[SierraItemRecord] shouldBe Seq(itemRecord)
          }
      }
    }
  }
}
