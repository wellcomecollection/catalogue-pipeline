package uk.ac.wellcome.platform.reindex.reindex_worker

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{CompleteReindexParameters, PartialReindexParameters}

class ReindexWorkerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with WorkerServiceFixture {

  it("sends a notification for every record that needs a reindex") {
    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination = createDestination

        withWorkerService(messageSender, queue, table, destination) { _ =>
          val records = createRecords(table, count = 5)

          val reindexParameters =
            CompleteReindexParameters(segment = 0, totalSegments = 1)

          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            messageSender
              .getMessages[NamedRecord] should contain theSameElementsAs records
          }
        }
      }
    }
  }

  it("can handle a partial reindex of the table") {
    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination = createDestination

        withWorkerService(messageSender, queue, table, destination) { _ =>
          createRecords(table, count = 5)

          val reindexParameters = PartialReindexParameters(maxRecords = 1)

          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            messageSender.messages should have size 1

            // Wait to check we aren't about to get more messages
            Thread.sleep(500)
            messageSender.messages should have size 1
          }
        }
      }
    }
  }
}
