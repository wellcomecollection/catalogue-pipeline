package uk.ac.wellcome.calm_adapter

import java.time.{Instant, LocalDate}

import akka.NotUsed
import akka.stream.scaladsl._
import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.{
  MemoryIndividualMessageSender,
  MemoryMessageSender
}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls
import scala.util.{Failure, Try}

class CalmAdapterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with SQS
    with Eventually
    with IntegrationPatience {

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
    val messageSender = new MemoryMessageSender()

    withCalmAdapterWorkerService(retriever, store, messageSender) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS(queue, CalmWindow(queryDate))
        eventually {
          store.entries shouldBe Map(
            Version("A", 0) -> recordA,
            Version("A", 1) -> recordA.copy(published = true),
            Version("B", 0) -> recordB,
            Version("B", 1) -> recordB.copy(published = true),
            Version("C", 0) -> recordC,
            Version("C", 1) -> recordC.copy(published = true)
          )
          retriever.previousQuery shouldBe Some(
            CalmQuery.ModifiedDate(queryDate))
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          messageSender.getMessages[Version[String, Int]] shouldBe List(
            Version("A", 0),
            Version("B", 0),
            Version("C", 0)
          )
        }
    }
  }

  it("should complete successfully when no records returned from the query") {
    val store = dataStore()
    val retriever = calmRetriever(Nil)
    val messageSender = new MemoryMessageSender()

    withCalmAdapterWorkerService(retriever, store, messageSender) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS(queue, CalmWindow(queryDate))
        Thread.sleep(1500)
        store.entries shouldBe Map.empty
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)

        messageSender.messages shouldBe empty
    }
  }

  it("should send the message to the DLQ if publishing of any record fails") {
    val retriever = new CalmRetriever {
      def apply(query: CalmQuery): Source[CalmRecord, NotUsed] = {
        val timestamp = Instant.now
        val records = List(
          CalmRecord("A", Map.empty, timestamp),
          CalmRecord("B", Map.empty, timestamp),
          CalmRecord("C", Map.empty, timestamp),
        )
        Source.fromIterator(() => records.toIterator)
      }
    }
    val brokenMessageSender = new MemoryMessageSender() {
      override val underlying: MemoryIndividualMessageSender =
        new MemoryIndividualMessageSender() {
          override def sendT[T](t: T)(subject: String, destination: String)(
            implicit encoder: Encoder[T]): Try[Unit] =
            if (t.asInstanceOf[Version[String, Int]].id == "B")
              Failure(new Exception("Waaah I couldn't send message"))
            else
              super.sendT(t)(subject, destination)
        }
    }

    withCalmAdapterWorkerService(retriever, messageSender = brokenMessageSender) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS(queue, CalmWindow(queryDate))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, size = 1)
    }
  }

  def withCalmAdapterWorkerService[R](
    retriever: CalmRetriever,
    store: MemoryStore[Key, CalmRecord] with Maxima[String, Int] = dataStore(),
    messageSender: MemoryMessageSender = new MemoryMessageSender())(
    testWith: TestWith[(CalmAdapterWorkerService[String], QueuePair), R]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withSQSStream[NotificationMessage, R](queue) { stream =>
            val calmAdapter = new CalmAdapterWorkerService(
              stream,
              messageSender = messageSender,
              retriever,
              new CalmStore(new MemoryVersionedStore(store))
            )
            calmAdapter.run()
            testWith((calmAdapter, QueuePair(queue, dlq)))
          }
      }
    }

  def calmRetriever(records: List[CalmRecord]) =
    new CalmRetriever {
      var previousQuery: Option[CalmQuery] = None
      def apply(query: CalmQuery): Source[CalmRecord, NotUsed] = {
        previousQuery = Some(query)
        Source.fromIterator(() => records.toIterator)
      }
    }

  def dataStore(entries: (Key, CalmRecord)*) =
    new MemoryStore(entries.toMap) with MemoryMaxima[String, CalmRecord]
}
