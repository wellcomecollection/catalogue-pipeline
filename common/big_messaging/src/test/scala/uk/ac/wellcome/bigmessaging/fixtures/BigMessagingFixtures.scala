package uk.ac.wellcome.bigmessaging.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.sqs.model.SendMessageResult
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.message._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  StoreWriteError,
  WriteError
}
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.store.TypedStore
import uk.ac.wellcome.storage.store.memory.{
  MemoryStore,
  MemoryStreamStore,
  MemoryStreamStoreEntry,
  MemoryTypedStore
}
import uk.ac.wellcome.storage.streaming.Codec
import scala.concurrent.ExecutionContext.Implicits.global

trait BigMessagingFixtures
    extends Akka
    with Matchers
    with SQS
    with SNS
    with S3Fixtures {

  case class ExampleObject(name: String)

  def withMessageStream[T, R](queue: SQS.Queue,
                              metrics: MemoryMetrics[StandardUnit] =
                                new MemoryMetrics[StandardUnit]())(
    testWith: TestWith[MessageStream[T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    typedStoreT: TypedStore[ObjectLocation, T]): R = {
    val stream = new MessageStream[T](
      sqsClient = asyncSqsClient,
      sqsConfig = createSQSConfigWith(queue),
      metrics = metrics
    )
    testWith(stream)
  }

  /** Send a MessageNotification to SQS.
    *
    * As if another application had used a MessageWriter to send the message
    * to an SNS topic, which was forwarded to the queue.  We don't use a
    * MessageWriter instance because that sends to SNS, not SQS.
    *
    * We always send an InlineNotification regardless of size, which makes for
    * slightly easier debugging if queue messages ever fail.
    *
    */
  def sendMessage[T](queue: Queue, obj: T)(
    implicit encoder: Encoder[T]): SendMessageResult =
    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = InlineNotification(jsonString = toJson(obj).get)
    )

  def createBrokenPutMemoryTypedStore[T]()(implicit codecT: Codec[T]) = {
    val memoryStore =
      new MemoryStore[ObjectLocation, MemoryStreamStoreEntry](Map.empty) {
        override def put(id: ObjectLocation)(t: MemoryStreamStoreEntry)
          : Either[WriteError,
                   Identified[ObjectLocation, MemoryStreamStoreEntry]] = {
          Left(StoreWriteError(new Throwable("BOOM!")))
        }
      }

    implicit val memoryStreamStore =
      new MemoryStreamStore[ObjectLocation](memoryStore)

    val memoryTypedStore =
      new MemoryTypedStore[ObjectLocation, T](Map.empty)

    memoryTypedStore
  }
}
