package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.sqs.model.SendMessageResult
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.message._
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.fixtures.S3

import scala.concurrent.ExecutionContext.Implicits.global

trait Messaging extends Akka with Matchers with SQS with SNS with S3 {

  case class ExampleObject(name: String)

  def withMessageStream[T, R](queue: SQS.Queue,
                              metrics: MemoryMetrics[StandardUnit] =
                                new MemoryMetrics[StandardUnit]())(
    testWith: TestWith[MessageStream[T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    objectStoreT: ObjectStore[T]): R = {
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
}
