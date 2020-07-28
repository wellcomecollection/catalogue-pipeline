package uk.ac.wellcome.transformer.common.worker

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{TransformedBaseWork, UnidentifiedWork}
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore

trait TestData
case object ValidTestData extends TestData
case object InvalidTestData extends TestData

object TestTransformer extends Transformer[TestData] with WorksGenerators {
  def apply(data: TestData, version: Int): Either[Exception, UnidentifiedWork] =
    data match {
      case ValidTestData   => Right(createUnidentifiedWork)
      case InvalidTestData => Left(new Exception("No No No"))
    }
}

class TestTransformerWorker(
  val stream: SQSStream[NotificationMessage],
  val sender: MemoryBigMessageSender[TransformedBaseWork],
  val store: VersionedStore[String, Int, TestData]
) extends TransformerWorker[TestData, String] {
  val transformer: Transformer[TestData] = TestTransformer
}

class TransformerWorkerTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with SQS
    with SNS {

  it("empties the queue if it can process everything") {
    val records = Map(
      Version("A", 1) -> ValidTestData,
      Version("B", 2) -> ValidTestData,
      Version("C", 3) -> ValidTestData
    )

    withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
      withWorker(queue, records = records) { _ =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))
        sendNotificationToSQS[Version[String, Int]](queue, Version("B", 2))
        sendNotificationToSQS[Version[String, Int]](queue, Version("C", 3))

        Thread.sleep(500)

        assertQueueEmpty(dlq)
        assertQueueEmpty(queue)
      }
    }
  }

  it("sends failed messages to the DLQ if it can't read from store") {
    withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
      withWorker(queue, records = Map.empty) { _ =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))

        Thread.sleep(2000)

        assertQueueHasSize(dlq, size = 1)
        assertQueueEmpty(queue)
      }
    }
  }

  it("sends failed messages to the DLQ if the transformer errors") {
    val records = Map(
      Version("A", 1) -> ValidTestData,
      Version("B", 2) -> ValidTestData,
      Version("C", 3) -> InvalidTestData
    )

    withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
      withWorker(queue, records = records) { _ =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))
        sendNotificationToSQS[Version[String, Int]](queue, Version("B", 2))
        sendNotificationToSQS[Version[String, Int]](queue, Version("C", 3))

        Thread.sleep(2000)

        assertQueueHasSize(dlq, size = 1)
        assertQueueEmpty(queue)
      }
    }
  }

  def withWorker[R](queue: Queue, records: Map[Version[String, Int], TestData])(
    testWith: TestWith[Unit, R]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { stream =>
        val store = MemoryVersionedStore[String, TestData](records)

        val worker = new TestTransformerWorker(
          stream = stream,
          sender = new MemoryBigMessageSender[TransformedBaseWork](),
          store = store
        )

        worker.run()
        testWith(())
      }
    }
}
