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
import uk.ac.wellcome.bigmessaging.s3.S3BigMessageSender
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore
import uk.ac.wellcome.storage.{Identified, ObjectLocation, StoreWriteError, WriteError}

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
                                   new MemoryMetrics[StandardUnit](),
                                 sqsClient: SqsAsyncClient = asyncSqsClient)(
    testWith: TestWith[BigMessageStream[T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    storeT: Store[ObjectLocation, T] = new MemoryStore[ObjectLocation, T](Map.empty)): R = {
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

  def withSqsBigMessageSender[T, R](bucket: Bucket,
                                    topic: Topic,
                                    senderSnsClient: SnsClient = snsClient,
                                    maxMessageSize: Int = 10000)(
    testWith: TestWith[BigMessageSender[SNSConfig], R]): R =
    testWith(
      S3BigMessageSender(
        bucketName = bucket.name,
        snsConfig = createSNSConfigWith(topic),
        maxMessageSize = maxMessageSize
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
  def createBrokenPutStore[T] =
    new MemoryStore[ObjectLocation, T](Map.empty) {
      override def put(id: ObjectLocation)(
        t: T): Either[WriteError, Identified[ObjectLocation, T]] = {
        Left(StoreWriteError(new Throwable("BOOM!")))
      }
    }
}
