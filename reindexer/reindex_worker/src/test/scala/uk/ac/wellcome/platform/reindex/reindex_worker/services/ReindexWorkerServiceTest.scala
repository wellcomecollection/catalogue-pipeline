package uk.ac.wellcome.platform.reindex.reindex_worker.services

import org.scalatest.concurrent.ScalaFutures
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
    with ScalaFutures
    with WorkerServiceFixture {

  it("completes a reindex") {
    withLocalDynamoDbTable { table =>
      val messageSender = new MemoryIndividualMessageSender()
      val destination = createDestination

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(messageSender, queue, table, destination) { _ =>
            val reindexParameters = CompleteReindexParameters(
              segment = 0,
              totalSegments = 1
            )

            val records = createRecords(table, count = 3)

            sendNotificationToSQS(
              queue = queue,
              message = createReindexRequestWith(parameters = reindexParameters)
            )

            eventually {
              messageSender
                .getMessages[NamedRecord] should contain theSameElementsAs records

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
      val destination = createDestination

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(messageSender, queue, table, destination) { _ =>
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

    val messageSender = new MemoryIndividualMessageSender()
    val destination = createDestination

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(messageSender, queue, badTable, destination) { _ =>
          sendNotificationToSQS(queue = queue, message = createReindexRequest)

          eventually {
            messageSender.messages shouldBe empty

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  it("fails if passed an invalid job ID") {
    withLocalDynamoDbTable { table =>
      val messageSender = new MemoryIndividualMessageSender()
      val destination = createDestination

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(
            messageSender,
            queue,
            configMap = Map("xyz" -> ((table, destination)))) { _ =>
            sendNotificationToSQS(
              queue = queue,
              message = createReindexRequestWith(jobConfigId = "abc")
            )

            eventually {
              messageSender.messages shouldBe empty

              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, 1)
            }
          }
      }
    }
  }

  it("selects the correct job config") {
    withLocalDynamoDbTable { table1 =>
      withLocalDynamoDbTable { table2 =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination1 = createDestination
        val destination2 = createDestination

        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            val records1 = createRecords(table1, count = 3)
            val records2 = createRecords(table2, count = 5)

            val configMap = Map(
              "1" -> ((table1, destination1)),
              "2" -> ((table2, destination2))
            )
            withWorkerService(messageSender, queue, configMap) { _ =>
              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "1")
              )

              eventually {
                messageSender.messages
                  .filter { _.destination == destination1 }
                  .map { msg =>
                    fromJson[NamedRecord](msg.body).get
                  } should contain theSameElementsAs records1

                messageSender.messages
                  .filter { _.destination == destination2 } shouldBe empty

                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }

              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "2")
              )

              eventually {
                messageSender.messages
                  .filter { _.destination == destination2 }
                  .map { msg =>
                    fromJson[NamedRecord](msg.body).get
                  } should contain theSameElementsAs records2

                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }
            }
        }
      }
    }
  }
}
