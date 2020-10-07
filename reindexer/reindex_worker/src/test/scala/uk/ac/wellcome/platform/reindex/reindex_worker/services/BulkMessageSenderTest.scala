package uk.ac.wellcome.platform.reindex.reindex_worker.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

class BulkMessageSenderTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with RandomGenerators {

  def createMessages(count: Int = 5): Seq[String] =
    (1 to count).map { _ =>
      randomAlphanumeric()
    }

  it("sends messages for the provided IDs") {
    val messageSender = new MemoryIndividualMessageSender()
    val bulkMessageSender = new BulkMessageSender(messageSender)

    val messages = createMessages()

    val future = bulkMessageSender.send(messages, destination = "messages")

    whenReady(future) { _ =>
      messageSender.messages.map { _.body } should contain theSameElementsAs messages
    }
  }

  it("sends messages to the right destination") {
    val messages1 = createMessages(count = 6)
    val messages2 = createMessages(count = 3)

    val messageSender = new MemoryIndividualMessageSender()
    val bulkMessageSender = new BulkMessageSender(messageSender)

    val future1 = bulkMessageSender.send(messages1, destination = "dst1")
    val future2 = bulkMessageSender.send(messages2, destination = "dst2")

    whenReady(Future.sequence(Seq(future1, future2))) { _ =>
      messageSender.messages
        .filter { _.destination == "dst1" }
        .map { _.body } should contain theSameElementsAs messages1

      messageSender.messages
        .filter { _.destination == "dst2" }
        .map { _.body } should contain theSameElementsAs messages2
    }
  }

  it("fails if the underlying message sender has an error") {
    val exception = new Throwable("BOOM!")

    val brokenSender = new MemoryIndividualMessageSender() {
      override def send(body: String)(subject: String,
                                      destination: String): Try[Unit] =
        if (messages.size > 5) {
          Failure(exception)
        } else {
          super.send(body)(subject, destination)
        }
    }
    val bulkMessageSender = new BulkMessageSender(brokenSender)

    val messages = createMessages(count = 10)

    val future = bulkMessageSender.send(messages, destination = "messages")

    whenReady(future.failed) {
      _ shouldBe exception
    }
  }
}
