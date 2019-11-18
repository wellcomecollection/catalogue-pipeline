package uk.ac.wellcome.mets_adapter.services

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.messaging.sns.SNSMessageSender
import uk.ac.wellcome.storage.{Version, Identified}
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

  def withWorkerService[R](bagRetriever: BagRetriever, internalStore: VersionedStore[String, Int, MetsData])(testWith: TestWith[(MetsAdapterWorkerService, QueuePair, SNS.Topic), R]) =
    withActorSystem { implicit actorSystem =>
      withLocalSnsTopic { topic =>
        withLocalSqsQueueAndDlq { case QueuePair(queue, dlq) =>
          withSQSStream[IngestUpdate, R](queue) { stream =>
            val workerService = new MetsAdapterWorkerService(
              stream,
              new SNSMessageSender(
                snsClient = snsClient,
                snsConfig = createSNSConfigWith(topic),
                subject = "SNSMessageSender"
              ),
              bagRetriever,
              new MetsStore(internalStore)
            )
            workerService.run()
            testWith((workerService, QueuePair(queue, dlq), topic))
          }
        }
      }
    }

  def createInternalStore(data: Map[Version[String, Int], MetsData] = Map.empty) =
    MemoryVersionedStore(data)

  def getMessages(topic: SNS.Topic): List[MetsData] =
    listMessagesReceivedFromSNS(topic)
      .map(msgInfo => fromJson[MetsData](msgInfo.message).get)
      .toList
}
