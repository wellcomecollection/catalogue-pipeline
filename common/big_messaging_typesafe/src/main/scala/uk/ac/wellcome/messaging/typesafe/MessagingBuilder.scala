package uk.ac.wellcome.messaging.typesafe

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.messaging.message.{
  MessageStream,
  MessageWriter,
  MessageWriterConfig
}
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder
import uk.ac.wellcome.storage.s3.S3StorageBackend
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object MessagingBuilder {
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

  def buildMessageWriterConfig(config: Config): MessageWriterConfig =
    MessageWriterConfig(
      snsConfig =
        SNSBuilder.buildSNSConfig(config, namespace = "message.writer"),
      s3Config = S3Builder.buildS3Config(config, namespace = "message.writer")
    )

  def buildMessageWriter[T](config: Config)(
    implicit
    encoderT: Encoder[T],
    codecT: Codec[T]): MessageWriter[T] = {
    implicit val storageBackend: S3StorageBackend = new S3StorageBackend(
      s3Client = S3Builder.buildS3Client(config)
    )

    new MessageWriter[T](
      messageConfig = buildMessageWriterConfig(config),
      snsClient = SNSBuilder.buildSNSClient(config)
    )
  }
}
