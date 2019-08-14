package uk.ac.wellcome.bigmessaging.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.sqs.model.SendMessageResult
import com.amazonaws.services.sns.AmazonSNS
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.message._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.{SNSConfig, SNSMessageSender}
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.{ObjectStore, StorageBackend}
import uk.ac.wellcome.storage.fixtures.S3
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.storage.streaming.Codec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

trait BigMessagingFixture
    extends Akka
    with MetricsSenderFixture
    with SQS
    with SNS
    with S3
    with Matchers {

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

  def withSqsBigMessageSender[T, R](bucket: Bucket,
                                    topic: Topic,
                                    senderSnsClient: AmazonSNS = snsClient)(
    testWith: TestWith[BigMessageSender[SNSConfig, T], R])(
    implicit
    encoderT: Encoder[T],
    codecT: Codec[T]): R = {

    val sender = new BigMessageSender[SNSConfig, T] {
      override val messageSender: MessageSender[SNSConfig] =
        new SNSMessageSender(
          snsClient = senderSnsClient,
          snsConfig = createSNSConfigWith(topic),
          subject = "Sent in MessagingIntegrationTest"
        )
      override val objectStore: ObjectStore[T] =
        new ObjectStore[T] {
          override implicit val codec: Codec[T] = codecT
          override implicit val storageBackend: StorageBackend =
            s3StorageBackend
        }
      override val namespace: String = bucket.name
      override implicit val encoder: Encoder[T] = encoderT
      override val maxMessageSize: Int = 10000
    }

    testWith(sender)
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

  /** Given a topic ARN which has received notifications containing pointers
    * to objects in S3, return the unpacked objects.
    */
  def getMessages[T](topic: Topic)(implicit decoder: Decoder[T]): List[T] =
    listMessagesReceivedFromSNS(topic).map { messageInfo =>
      fromJson[MessageNotification](messageInfo.message) match {
        case Success(RemoteNotification(location)) =>
          getObjectFromS3[T](location)
        case Success(InlineNotification(jsonString)) =>
          fromJson[T](jsonString).get
        case _ =>
          throw new RuntimeException(
            s"Unrecognised message: ${messageInfo.message}"
          )
      }
    }.toList
}
