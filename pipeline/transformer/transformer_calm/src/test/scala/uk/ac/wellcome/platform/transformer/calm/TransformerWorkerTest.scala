package uk.ac.wellcome.platform.transformer.calm

import akka.stream.{ActorMaterializer}
import com.amazonaws.services.cloudwatch.model.StandardUnit
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}

import scala.concurrent.ExecutionContext
import org.scalatest.{FunSpec, Matchers}
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

import scala.concurrent.ExecutionContext.Implicits.global

case class TestDataIn(data: List[String])

object TestTransformer extends Transformer[TestDataIn] {
  def apply(data: TestDataIn, version: Int) =
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
}

class TestTransformerWorker(
  val stream: SQSStream[NotificationMessage],
  val sender: MemoryBigMessageSender[TransformedBaseWork],
  val store: VersionedStore[String, Int, TestDataIn]
)(implicit val ec: ExecutionContext, val materializer: ActorMaterializer)
    extends TransformerWorker[TestDataIn, String] {
  val transformer = TestTransformer
}

trait TestJsonCodecs {
  implicit val versionEnc: Encoder[Version[String, Int]] = deriveEncoder
  implicit val notificationMessageEnc: Decoder[NotificationMessage] =
    deriveDecoder
}

class TransformerWorkerTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with SQS
    with SNS
    with TestJsonCodecs {

  it("If the stream fails, should eventually put the message on the DLQ") {

    withTransformerWorker(
      records = Map(
        Version("A", 1) -> TestDataIn(List("Hats", "Cats")),
        Version("B", 2) -> TestDataIn(List("Things", "Thangs")),
        Version("C", 3) -> TestDataIn(List("Pots", "Pans"))
      )
    ) {
      case (transformerWorker, QueuePair(queue, dlq), metrics) =>
        sendNotificationToSQS[Version[String, Int]](queue, Version("A", 1))
        sendNotificationToSQS[Version[String, Int]](queue, Version("B", 2))
        sendNotificationToSQS[Version[String, Int]](queue, Version("C", 3))

        Thread.sleep(500)

        assertQueueEmpty(dlq)
        assertQueueEmpty(queue)
        metrics.incrementedCounts should be(
          List(
            "TestTransformerWorker_ProcessMessage_success",
            "TestTransformerWorker_ProcessMessage_success",
            "TestTransformerWorker_ProcessMessage_success"))

    }
  }

  def withTransformerWorker[R](records: Map[Version[String, Int], TestDataIn])(
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
            val data: MemoryStore[Version[String, Int], TestDataIn]
              with Maxima[String, Int] =
              new MemoryStore(records) with MemoryMaxima[String, TestDataIn]
            val store = new MemoryVersionedStore[String, TestDataIn](data)

            withMaterializer { implicit materializer =>
              val transformerWorker =
                new TestTransformerWorker(stream, sender, store)

              transformerWorker.run()
              testWith((transformerWorker, QueuePair(queue, dlq), metrics))
            }
          }
      }
    }
}
