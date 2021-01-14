package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import io.circe.Decoder
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  CompleteReindexParameters,
  ReindexSource
}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import weco.catalogue.source_model.{
  CalmSourcePayload,
  MetsSourcePayload,
  MiroInventorySourcePayload,
  MiroSourcePayload,
  SierraSourcePayload,
  SourcePayload
}
import weco.catalogue.source_model.generators.MetsSourceDataGenerators
import weco.catalogue.source_model.mets.DeletedMetsFile

import java.time.Instant
import scala.collection.JavaConverters._
import java.util.UUID

class ReindexWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with WorkerServiceFixture
    with MetsSourceDataGenerators
    with S3ObjectLocationGenerators {

  // These tests are designed to check we can parse the data in DynamoDB
  // correctly.
  //
  // We deliberately use a low-level, Java-ish API so we can be explicit about
  // what the structure of the table looks like -- skipping the implicit conversions
  // of Scanamo and the like.
  //
  // e.g. rather than using the Instant converter provided by Scanamo, I've used
  // real values from our adapter tables.
  //
  // These examples are based on the table structure as of 14 December 2020.
  describe("completing a reindex") {
    it("for CALM records") {
      withLocalDynamoDbTable { table =>
        val calmRecordId = UUID.randomUUID().toString
        val location = createS3ObjectLocation
        val version = randomInt(from = 1, to = 10)

        dynamoClient.putItem(
          table.name,
          Map(
            "id" -> new AttributeValue(calmRecordId),
            "payload" -> new AttributeValue().withM(
              Map(
                "bucket" -> new AttributeValue(location.bucket),
                "key" -> new AttributeValue(location.key)
              ).asJava
            ),
            "version" -> new AttributeValue().withN(version.toString)
          ).asJava
        )

        val expectedMessage = CalmSourcePayload(
          id = calmRecordId,
          location = location,
          version = version
        )

        runTest(
          table = table,
          source = ReindexSource.Calm,
          expectedMessage = expectedMessage
        )
      }
    }

    it("for extant METS records") {
      withLocalDynamoDbTable { table =>
        val bibId = randomAlphanumeric()
        val sourceData = createMetsSourceDataWith(
          createdDate = Instant.parse("2019-09-21T22:10:11.343Z"),
          // DynamoDB doesn't let us pass an empty list of manifestations
          manifestations = List(randomAlphanumeric(), randomAlphanumeric())
        )
        val version = randomInt(from = 1, to = 10)

        dynamoClient.putItem(
          table.name,
          Map(
            "id" -> new AttributeValue(bibId),
            "payload" -> new AttributeValue().withM(
              Map(
                "MetsFileWithImages" -> new AttributeValue().withM(
                  Map(
                    "root" -> new AttributeValue().withM(
                      Map(
                        "bucket" -> new AttributeValue(sourceData.root.bucket),
                        "keyPrefix" -> new AttributeValue(sourceData.root.keyPrefix),
                      ).asJava
                    ),
                    "createdDate" -> new AttributeValue().withN("1569103811343"),
                    "filename" -> new AttributeValue(sourceData.filename),
                    "manifestations" -> new AttributeValue().withL(
                      sourceData.manifestations.map { new AttributeValue(_) }.asJava),
                    "version" -> new AttributeValue().withN(
                      sourceData.version.toString)
                  ).asJava
                )
              ).asJava
            ),
            "version" -> new AttributeValue().withN(version.toString)
          ).asJava
        )

        val expectedMessage = MetsSourcePayload(
          id = bibId,
          sourceData = sourceData,
          version = version
        )

        runTest(
          table = table,
          source = ReindexSource.Mets,
          expectedMessage = expectedMessage
        )
      }
    }

    it("for deleted METS records") {
      withLocalDynamoDbTable { table =>
        val bibId = randomAlphanumeric()
        val version = randomInt(from = 1, to = 10)

        val sourceData = DeletedMetsFile(
          createdDate = Instant.parse("2019-09-21T22:10:11.343Z"),
          version = version
        )

        dynamoClient.putItem(
          table.name,
          Map(
            "id" -> new AttributeValue(bibId),
            "payload" -> new AttributeValue().withM(
              Map(
                "DeletedMetsFile" -> new AttributeValue().withM(
                  Map(
                    "createdDate" -> new AttributeValue().withN("1569103811343"),
                    "version" -> new AttributeValue().withN(
                      sourceData.version.toString)
                  ).asJava
                )
              ).asJava
            ),
            "version" -> new AttributeValue().withN(version.toString)
          ).asJava
        )

        val expectedMessage = MetsSourcePayload(
          id = bibId,
          sourceData = sourceData,
          version = version
        )

        runTest(
          table = table,
          source = ReindexSource.Mets,
          expectedMessage = expectedMessage
        )
      }
    }

    it("for Miro records") {
      withLocalDynamoDbTable { table =>
        val miroID = randomAlphanumeric()
        val isClearedForCatalogueAPI = chooseFrom(true, false)
        val location = createS3ObjectLocation
        val version = randomInt(from = 1, to = 10)

        dynamoClient.putItem(
          table.name,
          Map(
            "id" -> new AttributeValue(miroID),
            "location" -> new AttributeValue().withM(
              Map(
                "bucket" -> new AttributeValue(location.bucket),
                "key" -> new AttributeValue(location.key)
              ).asJava
            ),
            "isClearedForCatalogueAPI" -> new AttributeValue()
              .withBOOL(isClearedForCatalogueAPI),
            "version" -> new AttributeValue().withN(version.toString)
          ).asJava
        )

        val expectedMessage = MiroSourcePayload(
          id = miroID,
          isClearedForCatalogueAPI = isClearedForCatalogueAPI,
          location = location,
          version = version
        )

        runTest(
          table = table,
          source = ReindexSource.Miro,
          expectedMessage = expectedMessage
        )
      }
    }

    it("for Miro inventory records") {
      withLocalDynamoDbTable { table =>
        val miroID = randomAlphanumeric()
        val location = createS3ObjectLocation
        val version = randomInt(from = 1, to = 10)

        dynamoClient.putItem(
          table.name,
          Map(
            "id" -> new AttributeValue(miroID),
            "location" -> new AttributeValue().withM(
              Map(
                "bucket" -> new AttributeValue(location.bucket),
                "key" -> new AttributeValue(location.key)
              ).asJava
            ),
            "version" -> new AttributeValue().withN(version.toString)
          ).asJava
        )

        val expectedMessage = MiroInventorySourcePayload(
          id = miroID,
          location = location,
          version = version
        )

        runTest(
          table = table,
          source = ReindexSource.MiroInventory,
          expectedMessage = expectedMessage
        )
      }
    }

    it("for Sierra inventory records") {
      withLocalDynamoDbTable { table =>
        val bibId = randomAlphanumeric()
        val location = createS3ObjectLocation
        val version = randomInt(from = 1, to = 10)

        dynamoClient.putItem(
          table.name,
          Map(
            "id" -> new AttributeValue(bibId),
            "payload" -> new AttributeValue().withM(
              Map(
                "bucket" -> new AttributeValue(location.bucket),
                "key" -> new AttributeValue(location.key)
              ).asJava
            ),
            "version" -> new AttributeValue().withN(version.toString)
          ).asJava
        )

        val expectedMessage = SierraSourcePayload(
          id = bibId,
          location = location,
          version = version
        )

        runTest(
          table = table,
          source = ReindexSource.Sierra,
          expectedMessage = expectedMessage
        )
      }
    }

    def runTest[T <: SourcePayload](
      table: Table,
      source: ReindexSource,
      expectedMessage: T)(implicit decoder: Decoder[T]): Assertion = {
      val messageSender = new MemoryIndividualMessageSender()
      val destination = createDestination

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(messageSender, queue, table, destination, source) {
            _ =>
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
                messageSender.getMessages[T] shouldBe Seq(expectedMessage)

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

      withLocalSqsQueuePair() {
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

    withLocalSqsQueuePair() {
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
      val source = chooseReindexSource

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(
            messageSender,
            queue,
            configMap = Map("xyz" -> ((table, destination, source)))) { _ =>
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
}
