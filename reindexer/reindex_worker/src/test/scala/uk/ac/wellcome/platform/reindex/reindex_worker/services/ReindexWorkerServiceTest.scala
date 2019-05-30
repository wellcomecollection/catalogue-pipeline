package uk.ac.wellcome.platform.reindex.reindex_worker.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.reindex.reindex_worker.models.CompleteReindexParameters
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table

class ReindexWorkerServiceTest
    extends FunSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with WorkerServiceFixture {

  it("completes a reindex") {
    withLocalDynamoDbTable { table =>
      val messageSender = new MemoryIndividualMessageSender()
      val records = createTableRecords(table, count = 5)

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, messageSender, table) { _ =>
            val reindexParameters = CompleteReindexParameters(
              segment = 0,
              totalSegments = 1
            )

            sendNotificationToSQS(
              queue = queue,
              message =
                createReindexRequestWith(parameters = reindexParameters)
            )

            eventually {
              val actualRecords = messageSender.getMessages[Record]

              actualRecords should contain theSameElementsAs records
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
      }
    }
  }

  it("fails if it cannot parse the SQS message as a ReindexJob") {
    withLocalDynamoDbTable { table =>
      val messageSender = new MemoryIndividualMessageSender()
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, messageSender, table) { _ =>
            sendNotificationToSQS(
              queue = queue,
              body = "<xml>What is JSON.</xl?>"
            )

            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, 1)
            }
          }
      }
    }
  }

  it("fails if the reindex job fails") {
    val badTable = Table(name = "doesnotexist", index = "whatindex")

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        val messageSender = new MemoryIndividualMessageSender()
        withWorkerService(queue, messageSender, badTable) { _ =>
          sendNotificationToSQS(queue = queue, message = createReindexRequest)

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  it("fails if passed an invalid job ID") {
    withLocalDynamoDbTable { table =>
      val messageSender = new MemoryIndividualMessageSender()
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, messageSender, configMap = Map("abc" -> ((table, "xyz")))) {
            _ =>
              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "123")
              )

              eventually {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, 1)
              }
          }
      }
    }
  }

  it("selects the correct job config") {
    val destination1 = "dst1"
    val destination2 = "dst2"

    val messageSender = new MemoryIndividualMessageSender()

    withLocalDynamoDbTable { table1 =>
      withLocalDynamoDbTable { table2 =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            val records1 = createTableRecords(table1)
            val records2 = createTableRecords(table2)

            val configMap = Map(
              "1" -> ((table1, destination1)),
              "2" -> ((table2, destination2))
            )
            withWorkerService(queue, messageSender, configMap = configMap) { _ =>
              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "1")
              )

              eventually {
                val actualRecords = messageSender.messages
                  .filter { _.destination == destination1 }
                  .map { _.body }
                  .map { fromJson[Record](_).get }

                actualRecords should contain theSameElementsAs records1

                messageSender.messages.filter { _.destination == destination2 } shouldBe empty

                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }

              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "2")
              )

              eventually {
                val actualRecords = messageSender.messages
                  .filter { _.destination == destination2 }
                  .map { _.body }
                  .map { fromJson[Record](_).get }

                actualRecords should contain theSameElementsAs records2

                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }
            }
        }
      }
    }
  }
}
