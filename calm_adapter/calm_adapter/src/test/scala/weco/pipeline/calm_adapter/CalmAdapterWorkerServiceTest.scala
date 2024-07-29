package weco.pipeline.calm_adapter

import java.time.{Instant, LocalDate}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl._
import io.circe.Encoder
import org.scalatest.EitherValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pekko.fixtures.Pekko
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.{
  MemoryIndividualMessageSender,
  MemoryMessageSender
}
import weco.messaging.sns.NotificationMessage
import weco.storage.{Identified, Version}
import weco.storage.maxima.memory.MemoryMaxima
import weco.storage.store.memory.MemoryStore
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS
import weco.catalogue.source_model.Implicits._
import weco.pipeline.calm_api_client.{CalmQuery, CalmQueryBase}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.util.{Failure, Try}

class CalmAdapterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with Pekko
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
    CalmRecord("B", Map("RecordID" -> List("B")), instantB)
  val recordC =
    CalmRecord("C", Map("RecordID" -> List("C")), instantC)
  val queryDate = LocalDate.of(2000, 1, 1)

  it("processes an incoming window, storing records and publishing keys") {
    val vhs = createSourceVHS[CalmRecord]
    val retriever = calmRetriever(List(recordA, recordB, recordC))
    val messageSender = new MemoryMessageSender()

    withCalmAdapterWorkerService(retriever, vhs, messageSender) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS[CalmQuery](
          queue,
          CalmQuery.ModifiedDate(queryDate)
        )
        eventually {
          vhs.underlying.getLatest("A").value shouldBe Identified(
            Version("A", 1),
            recordA.copy(published = true)
          )
          vhs.underlying.getLatest("B").value shouldBe Identified(
            Version("B", 1),
            recordB.copy(published = true)
          )
          vhs.underlying.getLatest("C").value shouldBe Identified(
            Version("C", 1),
            recordC.copy(published = true)
          )

          retriever.previousQuery shouldBe Some(
            CalmQuery.ModifiedDate(queryDate)
          )

          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }

          val expectedVersions = List(
            Version("A", 0),
            Version("B", 0),
            Version("C", 0)
          )

          messageSender
            .getMessages[Version[String, Int]] shouldBe expectedVersions

          messageSender
            .getMessages[CalmSourcePayload]
            .map {
              payload =>
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
          CalmQuery.ModifiedDate(queryDate)
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }

        messageSender.messages shouldBe empty
    }
  }

  it("sends the message to the DLQ if publishing of any record fails") {
    val retriever = new CalmRetriever {
      def apply(query: CalmQueryBase): Source[CalmRecord, NotUsed] = {
        val timestamp = Instant.now
        val records = List(
          CalmRecord("A", Map.empty, timestamp),
          CalmRecord("B", Map.empty, timestamp),
          CalmRecord("C", Map.empty, timestamp)
        )
        Source.fromIterator(() => records.toIterator)
      }
    }
    val brokenMessageSender = new MemoryMessageSender() {
      override val underlying: MemoryIndividualMessageSender =
        new MemoryIndividualMessageSender() {
          override def sendT[T](t: T)(subject: String, destination: String)(
            implicit encoder: Encoder[T]
          ): Try[Unit] =
            if (t.asInstanceOf[Version[String, Int]].id == "B")
              Failure(new Exception("Waaah I couldn't send message"))
            else
              super.sendT(t)(subject, destination)
        }
    }

    withCalmAdapterWorkerService(
      retriever,
      messageSender = brokenMessageSender
    ) {
      case (_, QueuePair(queue, dlq)) =>
        sendNotificationToSQS[CalmQuery](
          queue,
          CalmQuery.ModifiedDate(queryDate)
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  def withCalmAdapterWorkerService[R](
    retriever: CalmRetriever,
    vhs: SourceVHS[CalmRecord] = createSourceVHS[CalmRecord],
    messageSender: MemoryMessageSender = new MemoryMessageSender()
  )(testWith: TestWith[(CalmAdapterWorkerService[String], QueuePair), R]): R =
    withActorSystem {
      implicit actorSystem =>
        withLocalSqsQueuePair(visibilityTimeout = 1.second) {
          case QueuePair(queue, dlq) =>
            withSQSStream[NotificationMessage, R](queue) {
              stream =>
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
      var previousQuery: Option[CalmQueryBase] = None
      def apply(query: CalmQueryBase): Source[CalmRecord, NotUsed] = {
        previousQuery = Some(query)
        Source(records)
      }
    }

  def dataStore(entries: (Key, CalmRecord)*) =
    new MemoryStore(entries.toMap) with MemoryMaxima[String, CalmRecord]
}
