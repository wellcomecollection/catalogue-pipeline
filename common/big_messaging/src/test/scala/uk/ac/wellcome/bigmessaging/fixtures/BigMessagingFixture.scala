package uk.ac.wellcome.bigmessaging.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.cloudwatch.model.StandardUnit
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.model.SendMessageResult
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.bigmessaging.message._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.{SNSConfig, SNSMessageSender}
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.{
  Identified,
  ObjectLocation,
  StoreWriteError,
  WriteError
}
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.TypedStore
import uk.ac.wellcome.storage.store.memory.{
  MemoryStore,
  MemoryStreamStore,
  MemoryStreamStoreEntry,
  MemoryTypedStore
}
import uk.ac.wellcome.storage.streaming.Codec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

trait BigMessagingFixture
    extends Akka
    with Matchers
    with SQS
    with SNS
    with S3Fixtures {

  case class ExampleObject(name: String)

  def withBigMessageStream[T, R](queue: SQS.Queue,
                                 metrics: MemoryMetrics[StandardUnit] =
                                   new MemoryMetrics[StandardUnit]())(
    testWith: TestWith[BigMessageStream[T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    typedStoreT: TypedStore[ObjectLocation, T]): R = {
    val stream = new BigMessageStream[T](
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

  def withSqsBigMessageSender[T, R](
    bucket: Bucket,
    topic: Topic,
    senderSnsClient: AmazonSNS = snsClient,
    store: Option[MemoryTypedStore[ObjectLocation, T]] = None,
    bigMessageThreshold: Int = 10000)(
    testWith: TestWith[BigMessageSender[SNSConfig, T], R])(
    implicit
    encoderT: Encoder[T],
    codecT: Codec[T]): R =
    withSnsMessageSender(topic, senderSnsClient) { snsMessageSender =>
      val sender = new BigMessageSender[SNSConfig, T] {
        override val messageSender: MessageSender[SNSConfig] =
          snsMessageSender
        override val typedStore: MemoryTypedStore[ObjectLocation, T] =
          store.getOrElse(MemoryTypedStoreCompanion[ObjectLocation, T]())
        override val namespace: String = bucket.name
        override implicit val encoder: Encoder[T] = encoderT
        override val maxMessageSize: Int = bigMessageThreshold
      }
      testWith(sender)
    }

  def withSnsMessageSender[R](topic: Topic, snsClient: AmazonSNS = snsClient)(
    testWith: TestWith[MessageSender[SNSConfig], R]): R =
    testWith(
      new SNSMessageSender(
        snsClient = snsClient,
        snsConfig = createSNSConfigWith(topic),
        subject = "Sent in BigMessagingFixture"
      )
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

  /** The `.put` method on this store has been overriden to always
    * return a `Left[StoreWriteError]`
    */
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
