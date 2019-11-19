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
import uk.ac.wellcome.messaging.sns.SNSMessageSender
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
      bucket = "s3-bucket",
    ),
    "v1"
  )

  val bagRetriever =
    new BagRetriever {
      def getBag(update: IngestUpdate): Future[Option[Bag]] =
        Future.successful(Some(bag))
    }

  it("should process ingest updates and store and publish METS data") {
    val internalStore = createInternalStore()
    withWorkerService(bagRetriever, internalStore) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendSqsMessage(queue, IngestUpdate("space", "123"))
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        val metsData = getMessages(topic)
        metsData shouldEqual List(MetsData("root/mets.xml", 1))
        internalStore.getLatest("123") shouldBe Right(
          Identified(Version("123", 0), MetsData("root/mets.xml", 1))
        )
    }
  }

  it("should not publish METS data when already exists in the store") {
    val internalStore = createInternalStore(
      Map(Version("123", 0) -> MetsData("root/mets.xml", 1))
    )
    withWorkerService(bagRetriever, internalStore) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendSqsMessage(queue, IngestUpdate("space", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        val metsData = getMessages(topic)
        metsData shouldEqual Nil
        internalStore.getLatest("123") shouldBe Right(
          Identified(Version("123", 0), MetsData("root/mets.xml", 1))
        )
    }
  }

  it("should not store / publish anything when bag retrieval fails") {
    val internalStore = createInternalStore()
    val brokenBagRetriever = new BagRetriever {
      def getBag(update: IngestUpdate): Future[Option[Bag]] =
        Future.failed(new Exception("Failed retrieving bag"))
    }
    withWorkerService(brokenBagRetriever, internalStore) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendSqsMessage(queue, IngestUpdate("space", "123"))
        Thread.sleep(2000)
        assertQueueHasSize(queue, 0)
        assertQueueHasSize(dlq, 1)
        val metsData = getMessages(topic)
        metsData shouldEqual Nil
        internalStore.getLatest("123") shouldBe a[Left[_, _]]
    }
  }

  it("should not store METS data if publishing fails") {
    val internalStore = createInternalStore()
    withWorkerService(bagRetriever, internalStore, createBrokenMsgSender(_)) {
      case (workerService, QueuePair(queue, dlq), topic) =>
        sendSqsMessage(queue, IngestUpdate("space", "123"))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)
        val metsData = getMessages(topic)
        metsData shouldEqual Nil
        internalStore.getLatest("123") shouldBe a[Left[_, _]]
    }
  }

  def withWorkerService[R](bagRetriever: BagRetriever,
                           internalStore: VersionedStore[String, Int, MetsData],
                           createMsgSender: SNS.Topic => SNSMessageSender =
                             createMsgSender(_))(
    testWith: TestWith[(MetsAdapterWorkerService, QueuePair, SNS.Topic), R]) =
    withActorSystem { implicit actorSystem =>
      withLocalSnsTopic { topic =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withSQSStream[IngestUpdate, R](queue) { stream =>
              val workerService = new MetsAdapterWorkerService(
                stream,
                createMsgSender(topic),
                bagRetriever,
                new MetsStore(internalStore)
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

  def createInternalStore(
    data: Map[Version[String, Int], MetsData] = Map.empty) =
    MemoryVersionedStore(data)

  def getMessages(topic: SNS.Topic): List[MetsData] =
    listMessagesReceivedFromSNS(topic)
      .map(msgInfo => fromJson[MetsData](msgInfo.message).get)
      .toList
}
