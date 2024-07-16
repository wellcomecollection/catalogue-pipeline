package weco.pipeline.mets_adapter.services

import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import weco.pekko.fixtures.Pekko
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.generators.MetsSourceDataGenerators
import weco.catalogue.source_model.mets.MetsSourceData
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline.mets_adapter.models._
import weco.storage.fixtures.DynamoFixtures
import weco.storage.store.VersionedStore
import weco.storage.store.dynamo.DynamoSingleVersionStore
import weco.storage.store.memory.MemoryVersionedStore
import weco.storage.{Identified, Version}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds
import scala.util.{Failure, Try}

class MetsAdapterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Pekko
    with SQS
    with Eventually
    with IntegrationPatience
    with MetsSourceDataGenerators
    with DynamoFixtures {

  val bag = Bag(
    info = BagInfo("external-identifier"),
    manifest = BagManifest(
      List(
        BagFile("data/b30246039.xml", "mets.xml"),
        BagFile("objects/blahbluhblih.jp2", "blahbluhblih.jp2")
      )
    ),
    location = BagLocation(path = "root", bucket = "bucket"),
    version = "v1",
    createdDate = Instant.now
  )

  val bagRetriever =
    new BagRetriever {
      def getBag(space: String, externalIdentifier: String): Future[Bag] =
        Future.successful(bag)
    }

  val space = "digitised"
  val externalIdentifier = "123"
  val notification: BagRegistrationNotification =
    BagRegistrationNotification(
      space = space,
      externalIdentifier = externalIdentifier
    )

  val expectedVersion = Version(externalIdentifier, version = 1)

  val expectedData = createMetsSourceDataWith(
    bucket = bag.location.bucket,
    path = bag.location.path,
    file = "mets.xml",
    createdDate = bag.createdDate
  )

  it("processes ingest updates and store and publish METS data") {
    val store = createMetsStore

    withWorkerService(bagRetriever, store) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
            expectedVersion
          )
          messageSender.getMessages[MetsSourcePayload] shouldBe Seq(
            MetsSourcePayload(
              id = expectedVersion.id,
              version = expectedVersion.version,
              sourceData = expectedData
            )
          )

          store.getLatest(id = externalIdentifier) shouldBe Right(
            Identified(expectedVersion, expectedData)
          )
        }
    }
  }

  override def createTable(table: DynamoFixtures.Table): DynamoFixtures.Table =
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S
    )

  it("can receive the same update twice") {
    // DynamoDB can only handle second-level precision, so if we get a bag that has
    // a millisecond-level timestamp, we need to handle the fact that the createdDate
    // is going to be truncated.  In particular, we need to be able to double-send
    // the message and not fail when the truncated timestamp is different from the
    // original timestamp.
    val createdDate = Instant.parse("2001-01-01T01:01:01.123456Z")

    val expectedDataWithMilliseconds =
      expectedData.copy(createdDate = createdDate)

    val bagRetrieverWithMilliseconds =
      new BagRetriever {
        def getBag(space: String, externalIdentifier: String): Future[Bag] =
          Future.successful(bag.copy(createdDate = createdDate))
      }

    withLocalDynamoDbTable {
      table =>
        val store = new DynamoSingleVersionStore[String, MetsSourceData](
          createDynamoConfigWith(table)
        )

        withWorkerService(bagRetrieverWithMilliseconds, store) {
          case (_, QueuePair(queue, dlq), messageSender) =>
            sendNotificationToSQS(queue, notification)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }

            sendNotificationToSQS(queue, notification)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              messageSender.messages should have size 2

              messageSender
                .getMessages[Version[String, Int]]()
                .distinct shouldBe Seq(expectedVersion)
              messageSender
                .getMessages[MetsSourcePayload]
                .distinct shouldBe Seq(
                MetsSourcePayload(
                  id = expectedVersion.id,
                  version = expectedVersion.version,
                  sourceData = expectedDataWithMilliseconds
                )
              )

              store.getLatest(id = externalIdentifier) shouldBe Right(
                Identified(expectedVersion, expectedDataWithMilliseconds)
              )
            }
        }
    }
  }

  it("publishes new METS data when old version exists in the store") {
    val store = createMetsStoreWith(
      entries = Map(Version(externalIdentifier, 0) -> createMetsSourceData)
    )

    withWorkerService(bagRetriever, store) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)

        messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
          expectedVersion
        )
        messageSender.getMessages[MetsSourcePayload]() shouldBe Seq(
          MetsSourcePayload(
            id = expectedVersion.id,
            version = expectedVersion.version,
            sourceData = expectedData
          )
        )

        store.getLatest(id = externalIdentifier) shouldBe Right(
          Identified(expectedVersion, expectedData)
        )
    }
  }

  it("re-publishes existing data when current version exists in the store") {
    val store = createMetsStoreWith(
      entries = Map(Version(externalIdentifier, 1) -> expectedData)
    )

    withWorkerService(bagRetriever, store) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }

        messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
          expectedVersion
        )
        messageSender.getMessages[MetsSourcePayload] shouldBe Seq(
          MetsSourcePayload(
            id = expectedVersion.id,
            version = expectedVersion.version,
            sourceData = expectedData
          )
        )

        store.getLatest(id = externalIdentifier) shouldBe Right(
          Identified(expectedVersion, expectedData)
        )
    }
  }

  it("skips sending anything if there's already a newer version in the store") {
    val existingData = createMetsSourceData

    val store = createMetsStoreWith(
      entries = Map(Version(externalIdentifier, 2) -> existingData)
    )

    withWorkerService(bagRetriever, store) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }

        messageSender.messages shouldBe empty

        store.getLatest(id = externalIdentifier) shouldBe Right(
          Identified(Version(externalIdentifier, 2), existingData)
        )
    }
  }

  it("does not store / publish anything when bag retrieval fails") {
    val store = createMetsStore

    val brokenBagRetriever = new BagRetriever {
      def getBag(space: String, externalIdentifier: String): Future[Bag] =
        Future.failed(new Exception("Failed retrieving bag"))
    }

    val brokenMessageSender = new MemoryMessageSender {
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    withWorkerService(brokenBagRetriever, store, brokenMessageSender) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }

        messageSender.messages shouldBe empty

        store.getLatest(id = externalIdentifier) shouldBe a[Left[_, _]]
    }
  }

  it("stores METS data if publishing fails") {
    val store = createMetsStore

    val brokenMessageSender = new MemoryMessageSender {
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    withWorkerService(bagRetriever, store, brokenMessageSender) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }

        messageSender.messages shouldBe empty

        store.getLatest(id = externalIdentifier) shouldBe a[Right[_, _]]
    }
  }

  it(
    "sends message to the dlq if message is not wrapped in NotificationMessage"
  ) {
    withWorkerService(bagRetriever) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendSqsMessage(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)

          messageSender.messages shouldBe empty
        }
    }
  }

  describe("filtering by space") {
    it("processes updates in the digitised space") {
      val store = createMetsStore

      val notification = BagRegistrationNotification(
        space = "digitised",
        externalIdentifier = "123"
      )

      withWorkerService(bagRetriever, store) {
        case (_, QueuePair(queue, dlq), messageSender) =>
          sendNotificationToSQS(queue, notification)

          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
              expectedVersion
            )
            store.getLatest(id = externalIdentifier) shouldBe a[Right[_, _]]
          }
      }
    }
    it("processes updates in the born-digital space") {
      val store = createMetsStore

      val notification = BagRegistrationNotification(
        space = "born-digital",
        externalIdentifier = "123"
      )

      withWorkerService(bagRetriever, store) {
        case (_, QueuePair(queue, dlq), messageSender) =>
          sendNotificationToSQS(queue, notification)

          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
              expectedVersion
            )

            store.getLatest(id = externalIdentifier) shouldBe a[Right[_, _]]
          }
      }
    }
    it("doesn't process the update when storageSpace is something different") {
      val store = createMetsStore

      val notification = BagRegistrationNotification(
        space = "something-different",
        externalIdentifier = "123"
      )

      withWorkerService(bagRetriever, store) {
        case (_, QueuePair(queue, dlq), messageSender) =>
          sendNotificationToSQS(queue, notification)

          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            messageSender.messages shouldBe empty

            store.getLatest(id = externalIdentifier) shouldBe a[Left[_, _]]
          }
      }
    }
  }

  private def createMetsStore: MemoryVersionedStore[String, MetsSourceData] =
    createMetsStoreWith(entries = Map())

  private def createMetsStoreWith(
    entries: Map[Version[String, Int], MetsSourceData]
  ): MemoryVersionedStore[String, MetsSourceData] =
    MemoryVersionedStore[String, MetsSourceData](
      initialEntries = entries
    )

  def withWorkerService[R](
    bagRetriever: BagRetriever,
    metsStore: VersionedStore[String, Int, MetsSourceData] = createMetsStore,
    messageSender: MemoryMessageSender = new MemoryMessageSender()
  )(
    testWith: TestWith[
      (MetsAdapterWorkerService[String], QueuePair, MemoryMessageSender),
      R
    ]
  ): R =
    withActorSystem {
      implicit actorSystem =>
        withLocalSqsQueuePair(visibilityTimeout = 3 seconds) {
          case QueuePair(queue, dlq) =>
            withSQSStream[NotificationMessage, R](queue) {
              stream =>
                val workerService = new MetsAdapterWorkerService(
                  msgStream = stream,
                  msgSender = messageSender,
                  bagRetriever = bagRetriever,
                  metsStore = metsStore
                )
                workerService.run()
                testWith((workerService, QueuePair(queue, dlq), messageSender))
            }
        }
    }
}
