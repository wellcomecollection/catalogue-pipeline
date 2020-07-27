package uk.ac.wellcome.bigmessaging.message

import java.util.concurrent.ConcurrentLinkedQueue

import akka.stream.scaladsl.Flow
import io.circe.Decoder
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.generators.MemoryLocationGenerators
import uk.ac.wellcome.storage.providers.memory.MemoryLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.concurrent.Future
import scala.util.Random

class BigMessageStreamTest
    extends AnyFunSpec
    with Matchers
    with MemoryLocationGenerators
    with BigMessagingFixture {

  def process(list: ConcurrentLinkedQueue[ExampleObject])(
    o: ExampleObject): Future[Unit] = {
    list.add(o)
    Future.successful(())
  }

  describe("small messages (<256KB)") {
    it("reads messages off a queue, processes them and deletes them") {
      withMessageStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), _) =>
          val messages = createMessages(count = 3)

          messages.foreach { exampleObject =>
            sendInlineNotification(queue = queue, exampleObject = exampleObject)
          }

          val received = new ConcurrentLinkedQueue[ExampleObject]()

          messageStream.foreach(
            streamName = "test-stream",
            process = process(received))

          eventually {
            received should contain theSameElementsAs messages

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
      }
    }
  }

  describe("large messages (>256KB)") {
    it("reads messages off a queue, processes them and deletes them") {
      implicit val store: Store[MemoryLocation, ExampleObject] =
        new MemoryStore(Map.empty)

      withMessageStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), _) => {
          val messages = createMessages(count = 3)

          messages.foreach { exampleObject =>
            sendRemoteNotification(store, queue, exampleObject)
          }

          val received = new ConcurrentLinkedQueue[ExampleObject]()

          messageStream.foreach(
            streamName = "test-stream",
            process = process(received))

          eventually {
            received should contain theSameElementsAs messages

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
      }
    }
  }

  private def sendInlineNotification(queue: Queue,
                                     exampleObject: ExampleObject): Unit =
    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = InlineNotification(toJson(exampleObject).get)
    )

  private def sendRemoteNotification(
    store: Store[MemoryLocation, ExampleObject],
    queue: Queue,
    exampleObject: ExampleObject,
  ): Unit = {
    val path = Random.alphanumeric take 10 mkString
    val location = MemoryLocation(namespace = randomAlphanumeric, path = path)

    store.put(location)(exampleObject)

    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = MemoryRemoteNotification(location)
    )
  }

  private def createMessages(count: Int): List[ExampleObject] =
    (1 to count).map { idx =>
      ExampleObject("a" * idx)
    }.toList

  it("increments *_ProcessMessage metric when successful") {
    withMessageStreamFixtures {
      case (messageStream, QueuePair(queue, _), metrics) =>
        val exampleObject = ExampleObject("some value")
        sendInlineNotification(queue = queue, exampleObject = exampleObject)

        val received = new ConcurrentLinkedQueue[ExampleObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received)
        )

        eventually {
          metrics.incrementedCounts shouldBe Seq(
            "test-stream_ProcessMessage_success")
        }
    }
  }

  it("fails gracefully when NotificationMessage cannot be deserialised") {
    withMessageStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), metrics) =>
        sendInvalidJSONto(queue)

        val received = new ConcurrentLinkedQueue[ExampleObject]()

        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          metrics.incrementedCounts.foreach { entry =>
            entry should not endWith "_ProcessMessage_failure"
          }

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("does not fail gracefully when the s3 object cannot be retrieved") {
    withMessageStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), metrics) =>
        val streamName = "test-stream"

        val location = createMemoryLocation

        sendNotificationToSQS[MessageNotification](
          queue = queue,
          message = MemoryRemoteNotification(location)
        )

        val received = new ConcurrentLinkedQueue[ExampleObject]()

        messageStream.foreach(
          streamName = streamName,
          process = process(received))

        eventually {
          metrics.incrementedCounts shouldBe Seq(
            "test-stream_ProcessMessage_failure",
            "test-stream_ProcessMessage_failure",
            "test-stream_ProcessMessage_failure"
          )

          received shouldBe empty

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("continues reading if processing of some messages fails ") {
    withMessageStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), _) =>
        val exampleObject1 = ExampleObject("some value 1")
        val exampleObject2 = ExampleObject("some value 2")

        sendInvalidJSONto(queue)
        sendInlineNotification(queue = queue, exampleObject = exampleObject1)

        sendInvalidJSONto(queue)
        sendInlineNotification(queue = queue, exampleObject = exampleObject2)

        val received = new ConcurrentLinkedQueue[ExampleObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          received should contain theSameElementsAs List(
            exampleObject1,
            exampleObject2)

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 2)
        }
    }
  }

  describe("runStream") {
    it("processes messages off a queue") {
      withMessageStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), metrics) =>
          val exampleObject1 = ExampleObject("some value 1")
          sendInlineNotification(queue = queue, exampleObject = exampleObject1)
          val exampleObject2 = ExampleObject("some value 2")
          sendInlineNotification(queue = queue, exampleObject = exampleObject2)

          val received = new ConcurrentLinkedQueue[ExampleObject]()

          messageStream.runStream(
            "test-stream",
            source =>
              source.via(Flow.fromFunction {
                case (message, t) =>
                  received.add(t)
                  message
              }))

          eventually {
            metrics.incrementedCounts shouldBe Seq(
              "test-stream_ProcessMessage_success",
              "test-stream_ProcessMessage_success"
            )

            received should contain theSameElementsAs List(
              exampleObject1,
              exampleObject2)

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
      }
    }

    it("does not delete failed messages and sends a failure metric") {
      withMessageStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), metrics) =>
          val exampleObject = ExampleObject("some value")
          sendInlineNotification(queue = queue, exampleObject = exampleObject)

          messageStream.runStream(
            "test-stream",
            source =>
              source.via(
                Flow.fromFunction(_ => throw new RuntimeException("BOOOM!"))))

          eventually {
            metrics.incrementedCounts shouldBe Seq(
              "test-stream_ProcessMessage_failure",
              "test-stream_ProcessMessage_failure",
              "test-stream_ProcessMessage_failure"
            )

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
      }
    }

    it("continues reading if processing of some messages fails") {
      withMessageStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), _) =>
          val exampleObject1 = ExampleObject("some value 1")
          val exampleObject2 = ExampleObject("some value 2")

          sendInvalidJSONto(queue)
          sendInlineNotification(queue = queue, exampleObject = exampleObject1)

          sendInvalidJSONto(queue)
          sendInlineNotification(queue = queue, exampleObject = exampleObject2)

          val received = new ConcurrentLinkedQueue[ExampleObject]()
          messageStream.runStream(
            "test-stream",
            source =>
              source.via(Flow.fromFunction {
                case (message, t) =>
                  received.add(t)
                  message
              }))

          eventually {
            received should contain theSameElementsAs List(
              exampleObject1,
              exampleObject2)

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 2)
          }
      }
    }
  }

  private def withMessageStreamFixtures[R](
    testWith: TestWith[(BigMessageStream[MemoryLocation, ExampleObject],
                        QueuePair,
                        MemoryMetrics[StandardUnit]),
                       R]
  )(implicit
    decoderT: Decoder[ExampleObject],
    store: Store[MemoryLocation, ExampleObject] = new MemoryStore(Map.empty))
    : R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueuePair() {
        case queuePair @ QueuePair(queue, _) =>
          val metrics = new MemoryMetrics[StandardUnit]()
          withBigMessageStream[ExampleObject, R](queue, metrics) { stream =>
            testWith((stream, queuePair, metrics))
          }
      }
    }
}
