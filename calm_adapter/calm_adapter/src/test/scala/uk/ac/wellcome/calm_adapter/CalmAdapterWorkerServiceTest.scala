package uk.ac.wellcome.calm_adapter

import java.time.{Instant, LocalDate}

import akka.NotUsed
import akka.stream.scaladsl._
import io.circe.Encoder
import org.scalatest.EitherValues
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
import uk.ac.wellcome.platform.calm_api_client
import uk.ac.wellcome.platform.calm_api_client.{
  CalmQuery,
  CalmRecord,
  CalmRetriever
}
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.memory.MemoryStore
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls
import scala.util.{Failure, Try}

class CalmAdapterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with Akka
    with SQS
    with Eventually
    with IntegrationPatience
    with SourceVHSFixture {

  type Key = Version[String, Int]

  val instantA = Instant.ofEpochSecond(123456)
  val instantB = Instant.ofEpochSecond(instantA.getEpochSecond + 1)
  val instantC = Instant.ofEpochSecond(instantA.getEpochSecond + 2)
  val recordA = CalmRecord("A", Map("RecordID" -> List("A")), instantA)
  val recordB =
    calm_api_client.CalmRecord("B", Map("RecordID" -> List("B")), instantB)
  val recordC =
    calm_api_client.CalmRecord("C", Map("RecordID" -> List("C")), instantC)
  val queryDate = LocalDate.of(2000, 1, 1)

  it("processes an incoming window, storing records and publishing keys") {
    val vhs = createSourceVHS[CalmRecord]
    val retriever = calmRetriever(List(recordA, recordB, recordC))
    val messageSender = new MemoryMessageSender()

    withCalmAdapterWorkerService(retriever, vhs, messageSender) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS[CalmQuery](
          queue,
          CalmQuery.ModifiedDate(queryDate))
        eventually {
          vhs.underlying.getLatest("A").value shouldBe Identified(
            Version("A", 1),
            recordA.copy(published = true))
          vhs.underlying.getLatest("B").value shouldBe Identified(
            Version("B", 1),
            recordB.copy(published = true))
          vhs.underlying.getLatest("C").value shouldBe Identified(
            Version("C", 1),
            recordC.copy(published = true))

          retriever.previousQuery shouldBe Some(
            CalmQuery.ModifiedDate(queryDate))
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          val expectedVersions = List(
            Version("A", 0),
            Version("B", 0),
            Version("C", 0)
          )

          messageSender
            .getMessages[Version[String, Int]] shouldBe expectedVersions

          messageSender
            .getMessages[CalmSourcePayload]
            .map { payload =>
              Version(payload.id, payload.version)
            } shouldBe expectedVersions
        }
    }
  }

  it("completes successfully when no records returned from the query") {
    val retriever = calmRetriever(Nil)
    val messageSender = new MemoryMessageSender()

    withCalmAdapterWorkerService(retriever, messageSender = messageSender) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS[CalmQuery](
          queue,
          CalmQuery.ModifiedDate(queryDate))

        Thread.sleep(1500)
        assertQueueEmpty(queue)
        assertQueueEmpty(dlq)

        messageSender.messages shouldBe empty
    }
  }

  it("sends the message to the DLQ if publishing of any record fails") {
    val retriever = new CalmRetriever {
      def apply(query: CalmQuery): Source[CalmRecord, NotUsed] = {
        val timestamp = Instant.now
        val records = List(
          calm_api_client.CalmRecord("A", Map.empty, timestamp),
          calm_api_client.CalmRecord("B", Map.empty, timestamp),
          calm_api_client.CalmRecord("C", Map.empty, timestamp),
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
        sendNotificationToSQS[CalmQuery](
          queue,
          CalmQuery.ModifiedDate(queryDate))
        Thread.sleep(2000)
        assertQueueEmpty(queue)
        assertQueueHasSize(dlq, size = 1)
    }
  }

  def withCalmAdapterWorkerService[R](
    retriever: CalmRetriever,
    vhs: SourceVHS[CalmRecord] = createSourceVHS[CalmRecord],
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
              calmStore = new CalmStore(vhs)
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
