package uk.ac.wellcome.mets_adapter.services

import scala.util.{Failure, Try}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender

class MetsAdapterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with SQS
    with Eventually
    with IntegrationPatience {

  val bag = Bag(
    BagInfo("external-identifier"),
    BagManifest(
      List(
        BagFile("data/b30246039.xml", "mets.xml")
      )
    ),
    BagLocation(
      path = "root",
      bucket = "bucket",
    ),
    "v1"
  )

  val bagRetriever =
    new BagRetriever {
      def getBag(space: String, externalIdentifier: String): Future[Bag] =
        Future.successful(bag)
    }

  val space = "digitised"
  val externalIdentifier = "123"
  val notification: BagRegistrationNotification =
    createBagRegistrationNotificationWith(
      space = space,
      externalIdentifier = externalIdentifier
    )

  val expectedVersion = Version(externalIdentifier, version = 1)

  it("processes ingest updates and store and publish METS data") {
    val vhs = createStore()
    withWorkerService(bagRetriever, vhs) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
            expectedVersion)

          vhs.getLatest(id = externalIdentifier) shouldBe Right(
            Identified(expectedVersion, metsLocation())
          )
        }
    }
  }

  it("publishes new METS data when old version exists in the store") {
    val vhs = createStore(Map(Version(externalIdentifier, 0) -> "old-data"))
    withWorkerService(bagRetriever, vhs) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)

        messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
          expectedVersion)

        vhs.getLatest(id = externalIdentifier) shouldBe Right(
          Identified(expectedVersion, metsLocation())
        )
    }
  }

  it("re-publishes existing data when current version exists in the store") {
    val vhs =
      createStore(Map(Version(externalIdentifier, 1) -> "existing-data"))
    withWorkerService(bagRetriever, vhs) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)

        messageSender.getMessages[Version[String, Int]]() shouldBe Seq(
          expectedVersion)

        vhs.getLatest(id = externalIdentifier) shouldBe Right(
          Identified(expectedVersion, metsLocation("existing-data"))
        )
    }
  }

  it("ignores messages when greater version exists in the store") {
    val vhs =
      createStore(Map(Version(externalIdentifier, 2) -> "existing-data"))
    withWorkerService(bagRetriever, vhs) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, size = 1)

        messageSender.messages shouldBe empty

        vhs.getLatest(id = externalIdentifier) shouldBe Right(
          Identified(
            Version(externalIdentifier, 2),
            metsLocation("existing-data"))
        )
    }
  }

  it("should not store / publish anything when bag retrieval fails") {
    val vhs = createStore()
    val brokenBagRetriever = new BagRetriever {
      def getBag(space: String, externalIdentifier: String): Future[Bag] =
        Future.failed(new Exception("Failed retrieving bag"))
    }

    val brokenMessageSender = new MemoryMessageSender {
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    withWorkerService(brokenBagRetriever, vhs, brokenMessageSender) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, size = 1)

        messageSender.messages shouldBe empty

        vhs.getLatest(id = externalIdentifier) shouldBe a[Left[_, _]]
    }
  }

  it("should store METS data if publishing fails") {
    val vhs = createStore()

    val brokenMessageSender = new MemoryMessageSender {
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    withWorkerService(bagRetriever, vhs, brokenMessageSender) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, size = 1)

        messageSender.messages shouldBe empty

        vhs.getLatest(id = externalIdentifier) shouldBe a[Right[_, _]]
    }
  }

  it(
    "sends message to the dlq if message is not wrapped in NotificationMessage") {
    val vhs = createStore()
    withWorkerService(bagRetriever, vhs) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendSqsMessage(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)

        messageSender.messages shouldBe empty
    }
  }

  it("doesn't process the update when storageSpace isn't equal to 'digitised'") {
    val vhs = createStore()

    val notification = createBagRegistrationNotificationWith(
      space = "something-different",
      externalIdentifier = "123"
    )

    withWorkerService(bagRetriever, vhs) {
      case (_, QueuePair(queue, dlq), messageSender) =>
        sendNotificationToSQS(queue, notification)
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        messageSender.messages shouldBe empty
        vhs.getLatest(id = externalIdentifier) shouldBe a[Left[_, _]]
    }
  }

  def withWorkerService[R](bagRetriever: BagRetriever,
                           store: VersionedStore[String, Int, MetsLocation],
                           messageSender: MemoryMessageSender =
                             new MemoryMessageSender())(
    testWith: TestWith[(MetsAdapterWorkerService[String],
                        QueuePair,
                        MemoryMessageSender),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withSQSStream[NotificationMessage, R](queue) { stream =>
            val workerService = new MetsAdapterWorkerService(
              msgStream = stream,
              msgSender = messageSender,
              bagRetriever = bagRetriever,
              metsStore = new MetsStore(store)
            )
            workerService.run()
            testWith((workerService, QueuePair(queue, dlq), messageSender))
          }
      }
    }

  def createStore(data: Map[Version[String, Int], String] = Map.empty) =
    MemoryVersionedStore(data.mapValues(metsLocation(_)))

  def metsLocation(file: String = "mets.xml", version: Int = 1) =
    MetsLocation("bucket", "root", version, file, Nil)

  def createBagRegistrationNotificationWith(
    space: String,
    externalIdentifier: String): BagRegistrationNotification =
    BagRegistrationNotification(
      space = space,
      externalIdentifier = externalIdentifier)
}
