package uk.ac.wellcome.transformer.common.worker

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import WorkState.Source
import io.circe.Encoder

import scala.util.{Failure, Try}

trait TestData
case object ValidTestData extends TestData
case object InvalidTestData extends TestData

object TestTransformer extends Transformer[TestData] with WorkGenerators {
  def apply(data: TestData,
            version: Int): Either[Exception, Work.Visible[Source]] =
    data match {
      case ValidTestData   => Right(sourceWork())
      case InvalidTestData => Left(new Exception("No No No"))
    }
}

class TestTransformerWorker(
  val stream: SQSStream[NotificationMessage],
  val sender: MemoryMessageSender,
  store: VersionedStore[String, Int, TestData]
) extends TransformerWorker[TestData, String] {
  val transformer: Transformer[TestData] = TestTransformer

  override protected def lookupRecord(key: StoreKey): Either[ReadError, Identified[StoreKey, TestData]] =
    store.getLatest(key.id)
}

class TransformerWorkerTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Eventually
    with IntegrationPatience
    with Akka
    with SQS {

  it("empties the queue if it can process everything") {
    val records = Map(
      Version("A", 1) -> ValidTestData,
      Version("B", 2) -> ValidTestData,
      Version("C", 3) -> ValidTestData
    )

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorker(queue, records = records) { _ =>
          sendNotificationToSQS(queue, Version("A", 1))
          sendNotificationToSQS(queue, Version("B", 2))
          sendNotificationToSQS(queue, Version("C", 3))

          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
          }
        }
    }
  }

  it("sends a message to the next service") {
    val records = Map(
      Version("A", 1) -> ValidTestData,
      Version("B", 2) -> ValidTestData,
      Version("C", 3) -> ValidTestData
    )

    val sender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withWorker(queue, records = records, sender = sender) { _ =>
        sendNotificationToSQS(queue, Version("A", 1))
        sendNotificationToSQS(queue, Version("B", 2))
        sendNotificationToSQS(queue, Version("C", 3))

        eventually {
          assertQueueEmpty(queue)
          sender.getMessages[Work[Source]] should have size 3
        }
      }
    }
  }

  describe("sends failures to the DLQ") {
    it("if it can't parse the JSON on the queue") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue) { _ =>
            sendInvalidJSONto(queue)

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if it can't parse the notification as a Version[String, Int]") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue) { _ =>
            sendNotificationToSQS(queue, "not-a-version")

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if it can't find the source record in the store") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if the work can't be transformed") {
      val records = Map(
        Version("A", 1) -> ValidTestData,
        Version("B", 2) -> ValidTestData,
        Version("C", 3) -> InvalidTestData
      )

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, records = records) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))
            sendNotificationToSQS(queue, Version("B", 2))
            sendNotificationToSQS(queue, Version("C", 3))

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if it can't send a message") {
      val brokenSender = new MemoryMessageSender() {
        override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
          Failure(new Throwable("BOOM!"))
      }

      val records = Map(Version("A", 1) -> ValidTestData)

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, records = records, sender = brokenSender) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }
  }

  def withWorker[R](
    queue: Queue,
    records: Map[Version[String, Int], TestData] = Map.empty,
    sender: MemoryMessageSender = new MemoryMessageSender()
  )(
    testWith: TestWith[Unit, R]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { stream =>
        val store = MemoryVersionedStore[String, TestData](records)

        val worker = new TestTransformerWorker(
          stream = stream,
          sender = sender,
          store = store
        )

        worker.run()
        testWith(())
      }
    }
}
