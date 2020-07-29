package uk.ac.wellcome.bigmessaging.typesafe

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import io.circe.Decoder
import software.amazon.awssdk.services.sns.SnsClient
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.bigmessaging.s3.S3BigMessageSender
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.monitoring.typesafe.CloudWatchBuilder
import uk.ac.wellcome.storage.s3.{S3Config, S3ObjectLocation}
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object BigMessagingBuilder {
  def buildMessageStream[T](config: Config)(
    implicit actorSystem: ActorSystem,
    decoderT: Decoder[T],
    codecT: Codec[T]): BigMessageStream[T] = {

    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    implicit val store: Store[S3ObjectLocation, T] = S3TypedStore[T]

    new BigMessageStream[T](
      sqsClient = SQSBuilder.buildSQSAsyncClient(config),
      sqsConfig =
        SQSBuilder.buildSQSConfig(config, namespace = "message.reader"),
      metrics = CloudWatchBuilder.buildCloudWatchMetrics(config)
    )
  }

  def buildBigMessageSender(config: Config): BigMessageSender[SNSConfig] = {
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    val s3Config: S3Config =
      S3Builder.buildS3Config(config, namespace = "message.writer")

    implicit val snsClient: SnsClient = SNSBuilder.buildSNSClient(config)

    S3BigMessageSender(
      bucketName = s3Config.bucketName,
      snsConfig = SNSBuilder.buildSNSConfig(config),
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
      maxMessageSize = 250 * 1000
    )
  }
}
