package weco.pipeline.reindex_worker.services

import io.circe.Decoder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  PutItemRequest
}
import weco.catalogue.internal_model.locations.License
import weco.catalogue.source_model.generators.MetsSourceDataGenerators
import weco.catalogue.source_model.mets.DeletedMetsFile
import weco.catalogue.source_model.miro.{MiroSourceOverrides, MiroUpdateEvent}
import weco.catalogue.source_model._
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryIndividualMessageSender
import weco.pipeline.reindex_worker.fixtures.WorkerServiceFixture
import weco.pipeline.reindex_worker.models.{
  CompleteReindexParameters,
  ReindexSource
}
import weco.storage.fixtures.DynamoFixtures.Table
import weco.storage.generators.S3ObjectLocationGenerators

import java.time.Instant
import java.util
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ReindexWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with WorkerServiceFixture
    with MetsSourceDataGenerators
    with S3ObjectLocationGenerators {

  def toSingleAttributeValue(v: Any): AttributeValue =
    v match {
      case s: String     => AttributeValue.builder().s(s).build()
      case n: Int        => AttributeValue.builder().n(n.toString).build()
      case n: Long       => AttributeValue.builder().n(n.toString).build()
      case bool: Boolean => AttributeValue.builder().bool(bool).build()
      case m: Map[_, _] =>
        val convertedMap = toAttributeValue(
          m.asInstanceOf[Map[String, Any]].toSeq: _*
        )
        AttributeValue.builder().m(convertedMap).build()
      case l: List[_] =>
        val convertedList = l.map { toSingleAttributeValue }
        AttributeValue.builder().l(convertedList: _*).build()
    }

  def toAttributeValue(
    m: Tuple2[String, Any]*
  ): util.Map[String, AttributeValue] =
    m.map {
      case (key, value) => key -> toSingleAttributeValue(value)
      case other =>
        throw new IllegalArgumentException(s"Unexpected type in $m ($other)")
    }.toMap
      .asJava

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
    it("for extant CALM records") {
      withLocalDynamoDbTable {
        table =>
          val calmRecordId = UUID.randomUUID().toString
          val location = createS3ObjectLocation
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> calmRecordId,
                  "payload" -> Map(
                    "bucket" -> location.bucket,
                    "key" -> location.key
                  ),
                  "version" -> version
                )
              )
              .build()
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

    it("for deleted CALM records") {
      withLocalDynamoDbTable {
        table =>
          val calmRecordId = UUID.randomUUID().toString
          val location = createS3ObjectLocation
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> calmRecordId,
                  "payload" -> Map(
                    "bucket" -> location.bucket,
                    "key" -> location.key
                  ),
                  "version" -> version,
                  "isDeleted" -> true
                )
              )
              .build()
          )

          val expectedMessage = CalmSourcePayload(
            id = calmRecordId,
            location = location,
            version = version,
            isDeleted = true
          )

          runTest(
            table = table,
            source = ReindexSource.Calm,
            expectedMessage = expectedMessage
          )
      }
    }

    it("for extant METS records") {
      withLocalDynamoDbTable {
        table =>
          val bibId = randomAlphanumeric()
          val sourceData = createMetsSourceDataWith(
            createdDate = Instant.parse("2019-09-21T22:10:11.343Z"),
            // DynamoDB doesn't let us pass an empty list of manifestations
            manifestations = List(randomAlphanumeric(), randomAlphanumeric())
          )
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> bibId,
                  "payload" -> Map(
                    "MetsFileWithImages" -> Map(
                      "root" -> Map(
                        "bucket" -> sourceData.root.bucket,
                        "keyPrefix" -> sourceData.root.keyPrefix
                      ),
                      "createdDate" -> 1569103811343L,
                      "filename" -> sourceData.filename,
                      "manifestations" -> sourceData.manifestations,
                      "version" -> sourceData.version
                    )
                  ),
                  "version" -> version
                )
              )
              .build()
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
      withLocalDynamoDbTable {
        table =>
          val bibId = randomAlphanumeric()
          val version = randomInt(from = 1, to = 10)

          val sourceData = DeletedMetsFile(
            createdDate = Instant.parse("2019-09-21T22:10:11.343Z"),
            version = version
          )

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> bibId,
                  "payload" -> Map(
                    "DeletedMetsFile" -> Map(
                      "createdDate" -> 1569103811343L,
                      "version" -> sourceData.version
                    )
                  ),
                  "version" -> version
                )
              )
              .build()
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
      withLocalDynamoDbTable {
        table =>
          val miroID = randomAlphanumeric()
          val isClearedForCatalogueAPI = chooseFrom(true, false)
          val location = createS3ObjectLocation
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> miroID,
                  "location" -> Map(
                    "bucket" -> location.bucket,
                    "key" -> location.key
                  ),
                  "isClearedForCatalogueAPI" -> isClearedForCatalogueAPI,
                  "version" -> version
                )
              )
              .build()
          )

          val expectedMessage = MiroSourcePayload(
            id = miroID,
            isClearedForCatalogueAPI = isClearedForCatalogueAPI,
            location = location,
            events = List(),
            overrides = None,
            version = version
          )

          runTest(
            table = table,
            source = ReindexSource.Miro,
            expectedMessage = expectedMessage
          )
      }
    }

    it("for Miro records with a license override") {
      withLocalDynamoDbTable {
        table =>
          val miroID = randomAlphanumeric()
          val isClearedForCatalogueAPI = chooseFrom(true, false)
          val location = createS3ObjectLocation
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> miroID,
                  "location" -> Map(
                    "bucket" -> location.bucket,
                    "key" -> location.key
                  ),
                  "isClearedForCatalogueAPI" -> isClearedForCatalogueAPI,
                  "events" -> List(
                    Map(
                      "description" -> "Change license override from 'None' to 'cc-by-nc'",
                      "message" -> "An email from Jane Smith (the contributor) explained we can use CC-BY-NC",
                      "date" -> 1621417033533L,
                      "user" -> "Henry Wellcome <wellcomeh@wellcomecloud.onmicrosoft.com>"
                    )
                  ),
                  "overrides" -> Map(
                    "license" -> "cc-by"
                  ),
                  "version" -> version
                )
              )
              .build()
          )

          val expectedMessage = MiroSourcePayload(
            id = miroID,
            isClearedForCatalogueAPI = isClearedForCatalogueAPI,
            location = location,
            events = List(
              MiroUpdateEvent(
                description =
                  "Change license override from 'None' to 'cc-by-nc'",
                message =
                  "An email from Jane Smith (the contributor) explained we can use CC-BY-NC",
                date = Instant.parse("2021-05-19T09:37:13.533Z"),
                user =
                  "Henry Wellcome <wellcomeh@wellcomecloud.onmicrosoft.com>"
              )
            ),
            overrides = Some(
              MiroSourceOverrides(license = Some(License.CCBY))
            ),
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
      withLocalDynamoDbTable {
        table =>
          val miroID = randomAlphanumeric()
          val location = createS3ObjectLocation
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> miroID,
                  "location" -> Map(
                    "bucket" -> location.bucket,
                    "key" -> location.key
                  ),
                  "version" -> version
                )
              )
              .build()
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
      withLocalDynamoDbTable {
        table =>
          val bibId = randomAlphanumeric()
          val location = createS3ObjectLocation
          val version = randomInt(from = 1, to = 10)

          dynamoClient.putItem(
            PutItemRequest
              .builder()
              .tableName(table.name)
              .item(
                toAttributeValue(
                  "id" -> bibId,
                  "payload" -> Map(
                    "bucket" -> location.bucket,
                    "key" -> location.key
                  ),
                  "version" -> version
                )
              )
              .build()
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
      expectedMessage: T
    )(implicit decoder: Decoder[T]): Unit = {
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
    withLocalDynamoDbTable {
      table =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination = createDestination

        withLocalSqsQueuePair(visibilityTimeout = 1 second) {
          case QueuePair(queue, dlq) =>
            withWorkerService(messageSender, queue, table, destination) {
              _ =>
                sendNotificationToSQS(
                  queue = queue,
                  body = "<xml>What is JSON.</xl?>"
                )

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueHasSize(dlq, size = 1)
                }
            }
        }
    }
  }

  it("fails if the reindex job fails") {
    val badTable = Table(name = "doesnotexist", index = "whatindex")

    val messageSender = new MemoryIndividualMessageSender()
    val destination = createDestination

    withLocalSqsQueuePair(visibilityTimeout = 1 second) {
      case QueuePair(queue, dlq) =>
        withWorkerService(messageSender, queue, badTable, destination) {
          _ =>
            sendNotificationToSQS(queue = queue, message = createReindexRequest)

            eventually {
              messageSender.messages shouldBe empty

              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)
            }
        }
    }
  }

  it("fails if passed an invalid job ID") {
    withLocalDynamoDbTable {
      table =>
        val messageSender = new MemoryIndividualMessageSender()
        val destination = createDestination
        val source = chooseReindexSource

        withLocalSqsQueuePair(visibilityTimeout = 1 second) {
          case QueuePair(queue, dlq) =>
            withWorkerService(
              messageSender,
              queue,
              configMap = Map("xyz" -> ((table, destination, source)))
            ) {
              _ =>
                sendNotificationToSQS(
                  queue = queue,
                  message = createReindexRequestWith(jobConfigId = "abc")
                )

                eventually {
                  messageSender.messages shouldBe empty

                  assertQueueEmpty(queue)
                  assertQueueHasSize(dlq, size = 1)
                }
            }
        }
    }
  }
}
