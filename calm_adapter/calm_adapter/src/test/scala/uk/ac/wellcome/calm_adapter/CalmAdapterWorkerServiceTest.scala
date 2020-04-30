package uk.ac.wellcome.calm_adapter

import scala.util.{Failure, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls
import java.time.{Instant, LocalDate}
import org.scalatest.matchers.should.Matchers
import akka.stream.scaladsl._
import io.circe.Encoder

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSMessageSender}
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.json.JsonUtil._

class CalmAdapterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with SQS
    with SNS {

  type Key = Version[String, Int]

  val instantA = Instant.ofEpochSecond(123456)
  val instantB = Instant.ofEpochSecond(instantA.getEpochSecond + 1)
  val instantC = Instant.ofEpochSecond(instantA.getEpochSecond + 2)
  val recordA = CalmRecord("A", Map("RecordID" -> List("A")), instantA)
  val recordB = CalmRecord("B", Map("RecordID" -> List("B")), instantB)
  val recordC = CalmRecord("C", Map("RecordID" -> List("C")), instantC)
  val queryDate = LocalDate.of(2000, 1, 1)

  it("should process an incoming window, storing records and publishing keys") {
    val store = dataStore()
    val retriever = calmRetriever(List(recordA, recordB, recordC))
    withCalmAdapterWorkerService(retriever, store) {
      case (calmAdapter, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, CalmWindow(queryDate))
        Thread.sleep(500)
        store.entries shouldBe Map(
          Version("A", 0) -> recordA,
          Version("A", 1) -> recordA.copy(published = true),
          Version("B", 0) -> recordB,
          Version("B", 1) -> recordB.copy(published = true),
          Version("C", 0) -> recordC,
          Version("C", 1) -> recordC.copy(published = true)
        )
        retriever.previousQuery shouldBe Some(CalmQuery.ModifiedDate(queryDate))
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        getMessages(topic) shouldBe List(
          Version("A", 0),
          Version("B", 0),
          Version("C", 0)
        )
    }
  }

  it("should complete successfully when no records returned from the query") {
    val store = dataStore()
    val retriever = calmRetriever(Nil)
    withCalmAdapterWorkerService(retriever, store) {
      case (calmAdapter, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, CalmWindow(queryDate))
        Thread.sleep(500)
        store.entries shouldBe Map.empty
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)
        getMessages(topic) shouldBe Nil
    }
  }

  it("should send the message to the DLQ if publishing of any record fails") {
    val store = dataStore()
    val retriever = new CalmRetriever {
      def apply(query: CalmQuery) = {
        val timestamp = Instant.now
        val records = List(
          CalmRecord("A", Map.empty, timestamp),
          CalmRecord("B", Map.empty, timestamp),
          CalmRecord("C", Map.empty, timestamp),
        )
        Source.fromIterator(() => records.toIterator)
      }
    }
    val createBrokenMsgSender = (topic: SNS.Topic) =>
      new SNSMessageSender(
        snsClient = snsClient,
        snsConfig = createSNSConfigWith(topic),
        subject = "BrokenSNSMessageSender"
      ) {
        override def sendT[T](item: T)(
          implicit encoder: Encoder[T]): Try[Unit] = {
          if (item.asInstanceOf[Version[String, Int]].id == "B")
            Failure(new Exception("Waaah I couldn't send message"))
          else
            super.sendT(item)
        }
    }
    withCalmAdapterWorkerService(retriever, store, createBrokenMsgSender(_)) {
      case (calmAdapter, QueuePair(queue, dlq), topic) =>
        sendNotificationToSQS(queue, CalmWindow(queryDate))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, 1)
    }
  }

  def withCalmAdapterWorkerService[R](
    retriever: CalmRetriever,
    store: MemoryStore[Key, CalmRecord] with Maxima[String, Int],
    createMsgSender: SNS.Topic => SNSMessageSender = createMsgSender(_))(
    testWith: TestWith[(CalmAdapterWorkerService, QueuePair, SNS.Topic), R]) =
    withActorSystem { implicit actorSystem =>
      withLocalSnsTopic { topic =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withSQSStream[NotificationMessage, R](queue) { stream =>
              withMaterializer { implicit materializer =>
                val calmAdapter = new CalmAdapterWorkerService(
                  stream,
                  createMsgSender(topic),
                  retriever,
                  new CalmStore(new MemoryVersionedStore(store))
                )
                calmAdapter.run()
                testWith((calmAdapter, QueuePair(queue, dlq), topic))
              }
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

  def calmRetriever(records: List[CalmRecord]) =
    new CalmRetriever {
      var previousQuery: Option[CalmQuery] = None
      def apply(query: CalmQuery) = {
        previousQuery = Some(query)
        Source.fromIterator(() => records.toIterator)
      }
    }

  def dataStore(entries: (Key, CalmRecord)*) =
    new MemoryStore(entries.toMap) with MemoryMaxima[String, CalmRecord]

  def getMessages(topic: SNS.Topic) =
    listMessagesReceivedFromSNS(topic)
      .map(msgInfo => fromJson[Version[String, Int]](msgInfo.message).get)
      .toList
}
