package uk.ac.wellcome.platform.transformer.calm

import akka.stream.scaladsl.{Sink, Source}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}

import scala.concurrent.Await
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
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import scala.concurrent.duration._

case class TestDataIn(data: List[String])

class TestTransformerWorker(
  val stream: SQSStream[NotificationMessage],
  val sender: MemoryBigMessageSender[TransformedBaseWork],
  val store: VersionedStore[String, Int, TestDataIn],
  val transformer: Transformer[TestDataIn]
) extends TransformerWorker[TestDataIn, String] {
  val name = "TestTransformerWorker"
}

object TestTransformer extends Transformer[TestDataIn] {
  def transform(data: TestDataIn) =
    Right(
      UnidentifiedWork(
        sourceIdentifier = SourceIdentifier(
          value = "id",
          identifierType = CalmIdentifierTypes.recordId,
          ontologyType = "IdentifierType"),
        version = 1,
        data = WorkData()
      )
    )
}

class TransformerWorkerTest
    extends FunSpec
    with Matchers
    with Akka
    with SQS
    with SNS {
  it("Should succeed give a valid version and data from a store") {
    withMaterializer { implicit materializer =>
      withTransformerWorker(
        records = Map(
          Version("A", 5) -> TestDataIn(List("Things", "Bings"))
        )
      ) { transformerWorker =>
        {
          val message = new Message
          val notification =
            NotificationMessage(body = """{"id":"A","version":5}""")
          val source = Source(List((message, notification)))

          val sink = transformerWorker
            .withSource(source)
            .runWith(Sink.seq)

          val successes = Await.result(sink, 3.seconds)

          successes.size should be(1)
          transformerWorker.sender.messages.size should be(1)
        }
      }
    }
  }

  def withTransformerWorker[R](records: Map[Version[String, Int], TestDataIn])(
    testWith: TestWith[TestTransformerWorker, R]) =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withSQSStream[NotificationMessage, R](queue) { stream =>
            val sender = new MemoryBigMessageSender[TransformedBaseWork]()
            val data: MemoryStore[Version[String, Int], TestDataIn]
              with Maxima[String, Int] =
              new MemoryStore(records) with MemoryMaxima[String, TestDataIn]
            val store = new MemoryVersionedStore[String, TestDataIn](data)

            testWith(
              new TestTransformerWorker(stream, sender, store, TestTransformer))
          }
      }
    }
}
