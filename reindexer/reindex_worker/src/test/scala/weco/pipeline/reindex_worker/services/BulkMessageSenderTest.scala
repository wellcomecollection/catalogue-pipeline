package weco.pipeline.reindex_worker.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.RandomGenerators
import weco.json.JsonUtil._
import weco.messaging.memory.MemoryIndividualMessageSender

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

class BulkMessageSenderTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with RandomGenerators {

  case class NamedRecord(id: String, name: String)

  def createRecords(count: Int = 5): Seq[NamedRecord] =
    (1 to count).map {
      _ =>
        NamedRecord(id = randomAlphanumeric(), name = randomAlphanumeric())
    }

  it("sends messages for the provided IDs") {
    val messageSender = new MemoryIndividualMessageSender()
    val bulkMessageSender = new BulkMessageSender(messageSender)

    val records = createRecords()

    val future = bulkMessageSender.send(records, destination = "messages")

    whenReady(future) {
      _ =>
        messageSender.getMessages[NamedRecord] shouldBe records
    }
  }

  it("sends messages to the right destination") {
    val records1 = createRecords(count = 6)
    val records2 = createRecords(count = 3)

    val messageSender = new MemoryIndividualMessageSender()
    val bulkMessageSender = new BulkMessageSender(messageSender)

    val future1 = bulkMessageSender.send(records1, destination = "dst1")
    val future2 = bulkMessageSender.send(records2, destination = "dst2")

    whenReady(Future.sequence(Seq(future1, future2))) {
      _ =>
        messageSender.messages
          .filter { _.destination == "dst1" }
          .map { _.body }
          .map {
            fromJson[NamedRecord](_).get
          } should contain theSameElementsAs records1

        messageSender.messages
          .filter { _.destination == "dst2" }
          .map { _.body }
          .map {
            fromJson[NamedRecord](_).get
          } should contain theSameElementsAs records2
    }
  }

  it("fails if the underlying message sender has an error") {
    val exception = new Throwable("BOOM!")

    val brokenSender = new MemoryIndividualMessageSender() {
      override def send(
        body: String
      )(subject: String, destination: String): Try[Unit] =
        if (messages.size > 5) {
          Failure(exception)
        } else {
          super.send(body)(subject, destination)
        }
    }
    val bulkMessageSender = new BulkMessageSender(brokenSender)

    val records = createRecords(count = 10)

    val future = bulkMessageSender.send(records, destination = "messages")

    whenReady(future.failed) {
      _ shouldBe exception
    }
  }
}
