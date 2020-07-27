package uk.ac.wellcome.bigmessaging.fixtures

import akka.actor.ActorSystem
import io.circe.{Decoder, Encoder}
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message._
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.{SNSConfig, SNSMessageSender}
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.{Identified, StoreWriteError, WriteError}
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.providers.memory.MemoryLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

trait BigMessagingFixture
    extends Akka
    with Matchers
    with SQS
    with SNS
    with S3Fixtures {

  case class ExampleObject(name: String)

  def withBigMessageStream[T, R](
    queue: SQS.Queue,
    metrics: MemoryMetrics[StandardUnit] = new MemoryMetrics[StandardUnit](),
    sqsClient: SqsAsyncClient = asyncSqsClient
  )(testWith: TestWith[BigMessageStream[MemoryLocation, T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    storeT: Store[MemoryLocation, T]): R = {
    val stream = new BigMessageStream[MemoryLocation, T](
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

  def withSqsBigMessageSender[T, R](bucket: Bucket,
                                    topic: Topic,
                                    senderSnsClient: SnsClient = snsClient,
                                    storeT: Option[Store[MemoryLocation, T]] =
                                      None,
                                    bigMessageThreshold: Int = 10000)(
    testWith: TestWith[BigMessageSender[MemoryLocation, SNSConfig, T], R])(
    implicit
    encoderT: Encoder[T]): R =
    withSnsMessageSender(topic, senderSnsClient) { snsMessageSender =>
      val sender = new BigMessageSender[MemoryLocation, SNSConfig, T] {
        override val messageSender: MessageSender[SNSConfig] =
          snsMessageSender
        override val store: Store[MemoryLocation, T] =
          storeT.getOrElse(new MemoryStore(Map.empty))
        override val namespace: String = bucket.name
        override implicit val encoder: Encoder[T] = encoderT
        override val maxMessageSize: Int = bigMessageThreshold

        override def createLocation(namespace: String,
                                    key: String): MemoryLocation =
          MemoryLocation(namespace = namespace, path = key)

        override def createNotification(
          location: MemoryLocation): RemoteNotification[MemoryLocation] =
          MemoryRemoteNotification(location)
      }
      testWith(sender)
    }

  def withSnsMessageSender[R](topic: Topic, snsClient: SnsClient = snsClient)(
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
        case Success(S3RemoteNotification(location)) =>
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
  def createBrokenPutStore[T]: MemoryStore[MemoryLocation, T] =
    new MemoryStore[MemoryLocation, T](Map.empty) {
      override def put(id: MemoryLocation)(
        t: T): Either[WriteError, Identified[MemoryLocation, T]] = {
        Left(StoreWriteError(new Throwable("BOOM!")))
      }
    }
}
