package uk.ac.wellcome.platform.transformer.calm

import com.amazonaws.services.cloudwatch.model.StandardUnit
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}

import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.{
  SourceIdentifier,
  TransformedBaseWork,
  UnidentifiedWork,
  WorkData
}
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}

trait TestData
case object ValidTestData extends TestData
case object InvalidTestData extends TestData

object TestTransformer extends Transformer[TestData] {
  def apply(data: TestData, version: Int) =
    data match {
      case ValidTestData =>
        Right(
          UnidentifiedWork(
            sourceIdentifier = SourceIdentifier(
              value = "id",
              identifierType = CalmIdentifierTypes.recordId,
              ontologyType = "IdentifierType"),
            version = version,
            data = WorkData()
          )
        )

      case InvalidTestData => Left(new Exception("No No No"))
    }

}

class TestTransformerWorker(
  val stream: SQSStream[NotificationMessage],
  val sender: MemoryBigMessageSender[TransformedBaseWork],
  val store: VersionedStore[String, Int, TestData]
) extends TransformerWorker[TestData, String] {
  val transformer = TestTransformer
}

trait TestJsonCodecs {
  implicit val versionEnc: Encoder[Version[String, Int]] = deriveEncoder
  implicit val notificationMessageEnc: Decoder[NotificationMessage] =
    deriveDecoder
}

class TransformerWorkerTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with SQS
    with SNS
    with TestJsonCodecs {

  it("Should have an empty queue and DLQ if it can process all messages") {

    withTransformerWorker(
      records = Map(
        Version("A", 1) -> ValidTestData,
        Version("B", 2) -> ValidTestData,
        Version("C", 3) -> ValidTestData
      )
    ) {
      case (transformerWorker, QueuePair(queue, dlq), metrics) =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))
        sendNotificationToSQS[Version[String, Int]](queue, Version("B", 2))
        sendNotificationToSQS[Version[String, Int]](queue, Version("C", 3))

        Thread.sleep(500)

        assertQueueEmpty(dlq)
        assertQueueEmpty(queue)
    }
  }

  it(
    "Send failed messages to the DLQ and send to metrics if it can't read from store") {

    withTransformerWorker(
      records = Map(
        Version("A", 1) -> ValidTestData,
        Version("B", 2) -> ValidTestData,
        Version("C", 3) -> ValidTestData
      )
    ) {
      case (transformerWorker, QueuePair(queue, dlq), metrics) =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))
        sendNotificationToSQS[Version[String, Int]](queue, Version("B", 2))
        sendNotificationToSQS[Version[String, Int]](queue, Version("C", 3))
        sendNotificationToSQS[Version[String, Int]](queue, Version("D", 4))

        Thread.sleep(2000)

        assertQueueHasSize(dlq, 1)
        assertQueueEmpty(queue)
    }
  }

  it(
    "Send failed messages to the DLQ if it receives an error from the transformer") {

    withTransformerWorker(
      records = Map(
        Version("A", 1) -> ValidTestData,
        Version("B", 2) -> ValidTestData,
        Version("C", 3) -> InvalidTestData
      )
    ) {
      case (transformerWorker, QueuePair(queue, dlq), metrics) =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))
        sendNotificationToSQS[Version[String, Int]](queue, Version("B", 2))
        sendNotificationToSQS[Version[String, Int]](queue, Version("C", 3))

        Thread.sleep(2000)

        assertQueueHasSize(dlq, 1)
        assertQueueEmpty(queue)
    }
  }

  def withTransformerWorker[R](records: Map[Version[String, Int], TestData])(
    testWith: TestWith[(TestTransformerWorker,
                        SQS.QueuePair,
                        MemoryMetrics[StandardUnit]),
                       R]) =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          val metrics = new MemoryMetrics[StandardUnit]()
          withSQSStream[NotificationMessage, R](queue, metrics) { stream =>
            val sender = new MemoryBigMessageSender[TransformedBaseWork]()
            val data
              : MemoryStore[Version[String, Int], TestData] with Maxima[String,
                                                                        Int] =
              new MemoryStore(records) with MemoryMaxima[String, TestData]
            val store = new MemoryVersionedStore[String, TestData](data)

            val transformerWorker =
              new TestTransformerWorker(stream, sender, store)

            transformerWorker.run()
            testWith((transformerWorker, QueuePair(queue, dlq), metrics))
          }

      }
    }
}
