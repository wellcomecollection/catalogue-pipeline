package uk.ac.wellcome.mets_adapter.services

import scala.util.{Failure, Try}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{FunSpec, Matchers}
import io.circe.Encoder
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSMessageSender}
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.json.JsonUtil._

class MetsAdapterWorkerServiceTest
    extends FunSpec
    with Matchers
    with Akka
    with SQS
    with SNS {

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
      def getBag(update: IngestUpdate): Future[Bag] =
        Future.successful(bag)
    }

  it("processes ingest updates and store and publish METS data") {
    val vhs = createStore()
    withWorkerService(bagRetriever, vhs) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("digitised", "123"))
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        getMessages(topic) shouldEqual List(Version("123", 1))
        vhs.getLatest("123") shouldBe Right(
          Identified(Version("123", 1), metsLocation())
        )
    }
  }

  it("publishes new METS data when old version exists in the store") {
    val vhs = createStore(Map(Version("123", 0) -> "old-data"))
    withWorkerService(bagRetriever, vhs) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("digitised", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        getMessages(topic) shouldEqual List(Version("123", 1))
        vhs.getLatest("123") shouldBe Right(
          Identified(Version("123", 1), metsLocation())
        )
    }
  }

  it("re-publishes existing data when current version exists in the store") {
    val vhs = createStore(Map(Version("123", 1) -> "existing-data"))
    withWorkerService(bagRetriever, vhs) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("digitised", "123"))
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        getMessages(topic) shouldEqual List(Version("123", 1))
        vhs.getLatest("123") shouldBe Right(
          Identified(Version("123", 1), metsLocation("existing-data"))
        )
    }
  }

  it("ignores messages when greater version exists in the store") {
    val vhs = createStore(Map(Version("123", 2) -> "existing-data"))
    withWorkerService(bagRetriever, vhs) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("digitised", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)
        getMessages(topic) shouldEqual Nil
        vhs.getLatest("123") shouldBe Right(
          Identified(Version("123", 2), metsLocation("existing-data"))
        )
    }
  }

  it("should not store / publish anything when bag retrieval fails") {
    val vhs = createStore()
    val brokenBagRetriever = new BagRetriever {
      def getBag(update: IngestUpdate): Future[Bag] =
        Future.failed(new Exception("Failed retrieving bag"))
    }
    withWorkerService(brokenBagRetriever, vhs) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("digitised", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)
        getMessages(topic) shouldEqual Nil
        vhs.getLatest("123") shouldBe a[Left[_, _]]
    }
  }

  it("should store METS data if publishing fails") {
    val vhs = createStore()
    withWorkerService(bagRetriever, vhs, createBrokenMsgSender(_)) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("digitised", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)
        getMessages(topic) shouldEqual Nil
        vhs.getLatest("123") shouldBe a[Right[_, _]]
    }
  }

  it(
    "sends message to the dlq if message is not wrapped in NotificationMessage") {
    val vhs = createStore()
    withWorkerService(bagRetriever, vhs) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendSqsMessage(queue, ingestUpdate("digitised", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)
        getMessages(topic) shouldEqual Nil
    }
  }

  it("doesn't process the update when storageSpace isn't equal to 'digitised'") {
    val vhs = createStore()
    withWorkerService(bagRetriever, vhs, createBrokenMsgSender(_)) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, ingestUpdate("something-different", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        getMessages(topic) shouldEqual Nil
        vhs.getLatest("123") shouldBe a[Left[_, _]]
    }
  }

  def withWorkerService[R](bagRetriever: BagRetriever,
                           store: VersionedStore[String, Int, MetsLocation],
                           createMsgSender: SNS.Topic => SNSMessageSender =
                             createMsgSender(_))(
    testWith: TestWith[(MetsAdapterWorkerService, QueuePair, SNS.Topic), R]) =
    withActorSystem { implicit actorSystem =>
      withLocalSnsTopic { topic =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withSQSStream[NotificationMessage, R](queue) { stream =>
              val workerService = new MetsAdapterWorkerService(
                stream,
                createMsgSender(topic),
                bagRetriever,
                new MetsStore(store)
              )
              workerService.run()
              testWith((workerService, QueuePair(queue, dlq), topic))
            }
        }
      }
    }

  def createMsgSender(topic: SNS.Topic) =
    new SNSMessageSender(
      snsClient = snsClient,
      snsConfig = createSNSConfigWith(topic),
      subject = "SNSMessageSender"
    )

  def createBrokenMsgSender(topic: SNS.Topic) =
    new SNSMessageSender(
      snsClient = snsClient,
      snsConfig = createSNSConfigWith(topic),
      subject = "BrokenSNSMessageSender"
    ) {
      override def sendT[T](item: T)(implicit encoder: Encoder[T]): Try[Unit] =
        Failure(new Exception("Waaah I couldn't send message"))
    }

  def createStore(data: Map[Version[String, Int], String] = Map.empty) =
    MemoryVersionedStore(data.mapValues(metsLocation(_)))

  def metsLocation(file: String = "mets.xml", version: Int = 1) =
    MetsLocation("bucket", "root", version, file, Nil)

  def getMessages(topic: SNS.Topic) =
    listMessagesReceivedFromSNS(topic)
      .map(msgInfo => fromJson[Version[String, Int]](msgInfo.message).get)
      .toList

  def ingestUpdate(space: String, id: String) =
    IngestUpdate(IngestUpdateContext(space, id))
}
