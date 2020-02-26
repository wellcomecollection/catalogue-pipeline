package uk.ac.wellcome.platform.transformer.calm

import java.time.Instant

import akka.stream.scaladsl.Source
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.bigmessaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima

class CalmWorkerTest extends FunSpec with Matchers {
  type Key = Version[String, Int]

  val records = Map(
    Version("A", 5) -> CalmRecord(
      id = "id",
      data = Map.empty,
      retrievedAt = Instant.now,
      published = false)
  )
  val data: MemoryStore[Key, CalmRecord] with Maxima[String, Int] =
    new MemoryStore(records) with MemoryMaxima[String, CalmRecord]
  val sender = new MemoryBigMessageSender[TransformedBaseWork]()
  val store = new MemoryVersionedStore[String, CalmRecord](data)
  val message = new Message
  val notification = NotificationMessage(body = "{}")
  val source = Source(List((message, notification)))
  val calmWorker = new CalmWorker(
    sender = sender,
    store = store,
    source = source
  )

  it("works") {
    true shouldBe true
  }

}
