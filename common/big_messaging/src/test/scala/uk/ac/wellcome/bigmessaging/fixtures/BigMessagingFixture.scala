package uk.ac.wellcome.bigmessaging.fixtures

import akka.actor.ActorSystem
import io.circe.{Decoder, Encoder}
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.ac.wellcome.bigmessaging.message._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.concurrent.ExecutionContext.Implicits.global

trait BigMessagingFixture extends SQS {
  def withBigMessageStream[T, R](queue: SQS.Queue,
                                 metrics: MemoryMetrics[StandardUnit] =
                                   new MemoryMetrics[StandardUnit](),
                                 sqsClient: SqsAsyncClient = asyncSqsClient)(
    testWith: TestWith[BigMessageStream[T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    storeT: Store[ObjectLocation, T] =
      new MemoryStore[ObjectLocation, T](Map.empty)): R = {
    val stream = new BigMessageStream[T](
      sqsClient = sqsClient,
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
    implicit encoder: Encoder[T]): SendMessageResponse =
    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = InlineNotification(jsonString = toJson(obj).get)
    )
}
