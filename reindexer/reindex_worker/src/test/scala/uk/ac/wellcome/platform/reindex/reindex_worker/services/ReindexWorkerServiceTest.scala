package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.gu.scanamo.Scanamo
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.{DynamoFixtures, ReindexableTable, WorkerServiceFixture}
import uk.ac.wellcome.platform.reindex.reindex_worker.models.CompleteReindexParameters
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.vhs.Entry

class ReindexWorkerServiceTest
    extends FunSpec
    with Matchers
    with DynamoFixtures
    with ReindexableTable
    with WorkerServiceFixture {

  val exampleRecord = Entry(
    id = "id",
    version = 1,
    location = ObjectLocation(
      namespace = "s3://example-bukkit",
      key = "key.json.gz"
    ),
    metadata = ReindexerMetadata(name = "alex")
  )

  it("completes a reindex") {
    withLocalDynamoDbTable { table =>
      val messageSender = new MemoryIndividualMessageSender()
      val destination = "reindexes"

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, table, messageSender, destination) { _ =>
            val reindexParameters = CompleteReindexParameters(
              segment = 0,
              totalSegments = 1
            )

            Scanamo.put(dynamoDbClient)(table.name)(exampleRecord)

            sendNotificationToSQS(
              queue = queue,
              message =
                createReindexRequestWith(parameters = reindexParameters)
            )

            eventually {
              val actualRecords = messageSender.getMessages[ReindexerEntry]()

              actualRecords shouldBe List(exampleRecord)
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
      val destination = "reindexes"

      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, table, messageSender, destination) { _ =>
            sendNotificationToSQS(
              queue = queue,
              body = "<xml>What is JSON.</xl?>"
            )

            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)

              messageSender.messages shouldBe empty
            }
          }
      }
    }
  }

  it("fails if the reindex job fails") {
    val badTable = Table(name = "doesnotexist", index = "whatindex")

    val messageSender = new MemoryIndividualMessageSender()
    val destination = "reindexes"

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(queue, badTable, messageSender, destination) { _ =>
          sendNotificationToSQS(queue = queue, message = createReindexRequest)

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 1)

            messageSender.messages shouldBe empty
          }
        }
    }
  }

  it("fails if passed an invalid job ID") {
    val messageSender = new MemoryIndividualMessageSender()
    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(queue, messageSender, configMap = Map.empty) {
          _ =>
            sendNotificationToSQS(
              queue = queue,
              message = createReindexRequestWith(jobConfigId = "does-not-exist")
            )

            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)

              messageSender.messages shouldBe empty
            }
        }
    }
  }

  it("selects the correct job config") {
    val messageSender = new MemoryIndividualMessageSender()

    val destination1 = "destination1"
    val destination2 = "destination2"

    withLocalDynamoDbTable { table1 =>
      withLocalDynamoDbTable { table2 =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            val exampleRecord1 = exampleRecord.copy(id = "exampleRecord1")
            val exampleRecord2 = exampleRecord.copy(id = "exampleRecord2")

            Scanamo.put(dynamoDbClient)(table1.name)(exampleRecord1)
            Scanamo.put(dynamoDbClient)(table2.name)(exampleRecord2)

            val configMap = Map(
              "1" -> ((table1, destination1)),
              "2" -> ((table2, destination2))
            )
            withWorkerService(queue, messageSender, configMap) { _ =>
              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "1")
              )

              eventually {
                val receivedAtDestination1 =
                  messageSender.messages
                    .filter { _.destination == destination1 }
                    .map { _.body }
                    .map { fromJson[Entry[String, ReindexerMetadata]](_).get }

                receivedAtDestination1 shouldBe List(exampleRecord1)

                val receivedAtDestination2 =
                  messageSender.messages
                    .filter {
                      _.destination == destination2
                    }

                receivedAtDestination2 shouldBe empty

                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }

              sendNotificationToSQS(
                queue = queue,
                message = createReindexRequestWith(jobConfigId = "2")
              )

              eventually {
                val receivedAtDestination2 =
                  messageSender.messages
                    .filter { _.destination == destination2 }
                    .map { _.body }
                    .map { fromJson[Entry[String, ReindexerMetadata]](_).get }

                receivedAtDestination2 shouldBe List(exampleRecord2)

                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }
            }
        }
      }
    }
  }
}
