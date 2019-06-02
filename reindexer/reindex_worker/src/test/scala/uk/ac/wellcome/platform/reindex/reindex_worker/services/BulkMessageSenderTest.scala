package uk.ac.wellcome.platform.reindex.reindex_worker.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.memory.MemoryIndividualMessageSender

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Try}

class BulkMessageSenderTest extends FunSpec with Matchers with ScalaFutures {

  val messages: List[String] = (1 to 20).map { _ =>
    Random.alphanumeric take 15 mkString
  }.toList

  def createDestination: String = Random.alphanumeric.take(8) mkString

  it("sends messages for the provided IDs") {
    val messageSender = new MemoryIndividualMessageSender()
    val bulkSender = new BulkMessageSender(messageSender)

    val destination = createDestination

    val future = bulkSender.send(
      messages = messages,
      destination = destination
    )

    whenReady(future) { _ =>
      val receivedMessages = messageSender.messages
        .filter { _.destination == destination }
        .map { _.body }

      receivedMessages should contain theSameElementsAs messages
    }
  }

  it("returns a failed Future if one of the messages fails to send") {
    val exception = new Throwable("BOOM!")

    val messageSender = new MemoryIndividualMessageSender() {
      override def send(body: String)(subject: String,
                                      destination: String): Try[Unit] =
        if (messages.size > 5)
          Failure(exception)
        else
          super.send(body)(subject, destination)
    }

    val bulkSender = new BulkMessageSender(messageSender)

    val destination = createDestination

    val future = bulkSender.send(
      messages = messages,
      destination = destination
    )

    whenReady(future.failed) {
      _ shouldBe exception
    }
  }
}
