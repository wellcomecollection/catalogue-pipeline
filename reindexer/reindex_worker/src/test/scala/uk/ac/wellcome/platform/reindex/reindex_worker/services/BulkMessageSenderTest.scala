package uk.ac.wellcome.platform.reindex.reindex_worker.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Try}

class BulkMessageSenderTest
    extends FunSpec
    with Matchers
    with ScalaFutures {

  val messages: List[String] = (1 to 3).map { _ =>
    Random.alphanumeric take 15 mkString
  }.toList

  def createBulkSender(messageSender: MemoryIndividualMessageSender): BulkMessageSender[String] =
    new BulkMessageSender[String](messageSender)

  it("sends messages for the provided IDs") {
    val messageSender = new MemoryIndividualMessageSender()
    val bulkSender = createBulkSender(messageSender)

    val future = bulkSender.send(
      messages = messages,
      destination = "reindexes"
    )

    whenReady(future) { _ =>
      val actualRecords =
        messageSender.messages
          .filter { _.destination == "reindexes" }
          .map { _.body }

      actualRecords should contain theSameElementsAs messages

      messageSender.messages.filterNot { _.destination == "reindexes" } shouldBe empty
    }
  }

  it("returns a failed Future if the underlying sender fails") {
    val brokenSender = new MemoryIndividualMessageSender() {
      override def send(body: String)(subject: String, destination: String): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    val bulkSender = createBulkSender(brokenSender)

    val future = bulkSender.send(
      messages = messages,
      destination = "reindexes"
    )

    whenReady(future.failed) { err =>
      err shouldBe a[Throwable]
      err.getMessage shouldBe "BOOM!"

      brokenSender.messages shouldBe empty
    }
  }
}
