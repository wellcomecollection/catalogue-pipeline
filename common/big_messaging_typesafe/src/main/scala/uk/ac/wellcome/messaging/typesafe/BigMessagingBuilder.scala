package uk.ac.wellcome.messaging.typesafe

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.messaging.message.MessageStream
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.{BigMessageSender, MessageSender}
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.s3.{S3Config, S3StorageBackend}
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object BigMessagingBuilder {
  def buildMessageStream[T](config: Config)(
    implicit actorSystem: ActorSystem,
    decoderT: Decoder[T],
    materializer: ActorMaterializer,
    codecT: Codec[T]): MessageStream[T] = {
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    implicit val storageBackend: S3StorageBackend = new S3StorageBackend(
      s3Client = S3Builder.buildS3Client(config)
    )

    new MessageStream[T](
      sqsClient = SQSBuilder.buildSQSAsyncClient(config),
      sqsConfig =
        SQSBuilder.buildSQSConfig(config, namespace = "message.reader"),
      metricsSender = MetricsBuilder.buildMetricsSender(config)
    )
  }

  def buildBigMessageSender[T](config: Config)(
    implicit
    encoderT: Encoder[T],
    codecT: Codec[T]): BigMessageSender[SNSConfig, T] = {
    implicit val storageBackend: S3StorageBackend = new S3StorageBackend(
      s3Client = S3Builder.buildS3Client(config)
    )

    val s3Config: S3Config =
      S3Builder.buildS3Config(config, namespace = "message.writer")

    new BigMessageSender[SNSConfig, T] {
      override val messageSender: MessageSender[SNSConfig] =
        SNSBuilder.buildSNSMessageSender(
          config,
          namespace = "message.writer",
          subject = "Sent from MessageWriter"
        )

      override val objectStore: ObjectStore[T] = ObjectStore[T]
      override val namespace: String = s3Config.bucketName

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
      override val maxMessageSize: Int = 250 * 1000
    }
  }
}
