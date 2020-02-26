package uk.ac.wellcome.platform.transformer.calm

import java.time.Instant

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.bigmessaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima

import scala.concurrent.Await
import scala.concurrent.duration._

class CalmTransformerWorkerTest extends FunSpec with Matchers with Akka {
  it("Never succeeds running a flow with an invalid source") {
    withMaterializer { implicit materializer =>
      withCalmTransformerWorker(
        records = Map(
          Version("A", 5) -> CalmRecord(
            id = "id",
            data = Map.empty,
            retrievedAt = Instant.now,
            published = false)
        )
      ) { calmTransformerWorker =>
        {
          val message = new Message
          val notification = NotificationMessage(body = "{}")
          val source = Source(List((message, notification)))

          val sink = calmTransformerWorker
            .withSource(source)
            .runWith(Sink.seq)

          val successes = Await.result(sink, 3.seconds)

          successes.size should be(0)
        }
      }
    }
  }

  def withCalmTransformerWorker[R](
    records: Map[Version[String, Int], CalmRecord])(
    testWith: TestWith[CalmTransformerWorker[String], R]) = {

    type Key = Version[String, Int]

    val data: MemoryStore[Key, CalmRecord] with Maxima[String, Int] =
      new MemoryStore(records) with MemoryMaxima[String, CalmRecord]
    val sender = new MemoryBigMessageSender[TransformedBaseWork]()
    val store = new MemoryVersionedStore[String, CalmRecord](data)

    val calmTransformerWorker = new CalmTransformerWorker(
      sender = sender,
      store = store
    )

    testWith(calmTransformerWorker)
  }

}
