package uk.ac.wellcome.bigmessaging.message

import java.util.concurrent.ConcurrentLinkedQueue

import akka.stream.scaladsl.Flow
import com.amazonaws.services.cloudwatch.model.StandardUnit
import io.circe.Decoder
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.bigmessaging.fixtures.MessagingFixtures
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.{TypedStore, TypedStoreEntry}
import scala.collection.immutable.Map
import scala.concurrent.Future
import scala.util.Random

class MessageStreamTest extends FunSpec with Matchers with MessagingFixtures {

  def process(list: ConcurrentLinkedQueue[ExampleObject])(o: ExampleObject) = {
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
      implicit val typedStore: TypedStore[ObjectLocation, ExampleObject] =
        MemoryTypedStoreCompanion[ObjectLocation, ExampleObject]

      withMessageStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), _) => {
          val messages = createMessages(count = 3)

          messages.foreach { exampleObject =>
            sendRemoteNotification(typedStore, queue, exampleObject)
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
    typedStore: TypedStore[ObjectLocation, ExampleObject],
    queue: Queue,
    exampleObject: ExampleObject,
  ): Unit = {
    val s3key = Random.alphanumeric take 10 mkString
    val location = ObjectLocation(namespace = randomAlphanumeric, path = s3key)

    typedStore.put(location)(
      TypedStoreEntry(exampleObject, metadata = Map.empty))

    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = RemoteNotification(location)
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

        // Do NOT put S3 object here
        val objectLocation = ObjectLocation(
          namespace = "bukkit",
          path = "path.json"
        )

        sendNotificationToSQS[MessageNotification](
          queue = queue,
          message = RemoteNotification(objectLocation)
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
    testWith: TestWith[(MessageStream[ExampleObject],
                        QueuePair,
                        MemoryMetrics[StandardUnit]),
                       R]
  )(implicit
    decoderT: Decoder[ExampleObject],
    typedStore: TypedStore[ObjectLocation, ExampleObject] =
      MemoryTypedStoreCompanion[ObjectLocation, ExampleObject]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, _) =>
          val metrics = new MemoryMetrics[StandardUnit]()
          withMessageStream[ExampleObject, R](queue, metrics) { stream =>
            testWith((stream, queuePair, metrics))
          }
      }
    }
}
