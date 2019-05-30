package uk.ac.wellcome.platform.reindex.reindex_worker

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.reindex.reindex_worker.models.CompleteReindexParameters

import scala.util.Random

class ReindexWorkerFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with WorkerServiceFixture {

  it("sends a notification for every record that needs a reindex") {
    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination = Random.alphanumeric.take(8) mkString

        val testRecords = createTableRecords(table)

        val reindexParameters = CompleteReindexParameters(
          segment = 0,
          totalSegments = 1
        )

        withWorkerService(queue, messageSender, table, destination) { _ =>
          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            messageSender.getMessages[Record] should contain theSameElementsAs testRecords
          }
        }
      }
    }
  }

  it("can handle a partial reindex of the table") {
    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination = Random.alphanumeric.take(8) mkString

        val testRecords = createTableRecords(table)

        val reindexParameters = CompleteReindexParameters(
          segment = 0,
          totalSegments = 1
        )

        withWorkerService(queue, messageSender, table, destination) { _ =>
          sendNotificationToSQS(
            queue = queue,
            message = createReindexRequestWith(parameters = reindexParameters)
          )

          eventually {
            val actualRecords = messageSender.getMessages[Record]

            actualRecords should have length 1
            actualRecords should contain theSameElementsAs Seq(
              testRecords.head)
          }
        }
      }
    }
  }
}
