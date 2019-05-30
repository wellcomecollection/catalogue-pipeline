package uk.ac.wellcome.messaging.message

import com.amazonaws.services.sns.AmazonSNS
import grizzled.slf4j.Logging
import io.circe.Encoder
import uk.ac.wellcome.messaging.sns.{SNSConfig, SNSMessageSender}
import uk.ac.wellcome.messaging.{BigMessageSender, MessageSender}
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.s3.S3Config

import scala.concurrent.Future

case class MessageWriterConfig(
  snsConfig: SNSConfig,
  s3Config: S3Config
)

class MessageWriter[T](
  messageConfig: MessageWriterConfig,
  snsClient: AmazonSNS
)(implicit objectStoreT: ObjectStore[T], encoderT: Encoder[T])
    extends Logging {

  private val underlying = new BigMessageSender[SNSConfig, T] {
    override val messageSender: MessageSender[SNSConfig] = new SNSMessageSender(
      snsClient = snsClient,
      snsConfig = messageConfig.snsConfig,
      subject = "Sent from MessageWriter"
    )

    override val objectStore: ObjectStore[T] = objectStoreT
    override val namespace: String = messageConfig.s3Config.bucketName

    implicit val encoder: Encoder[T] = encoderT

    // If the encoded message is less than 250KB, we can send it inline
    // in SNS/SQS (although the limit is 256KB, there's a bit of overhead
    // caused by the notification wrapper, so we're conservative).
    //
    // Max SQS message size:
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-limits.html#limits-messages
    //
    // Max SNS message size:
    // https://aws.amazon.com/sns/faqs/
    //
    override val maxMessageSize: Int = 250 * 100
  }

  def write(t: T): Future[MessageNotification] = Future.fromTry {
    underlying.sendT(t)
  }
}
