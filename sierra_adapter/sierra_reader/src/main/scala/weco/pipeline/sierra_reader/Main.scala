package weco.pipeline.sierra_reader

import akka.actor.ActorSystem
import com.typesafe.config.Config
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.transfer.s3.S3TransferManager
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.SQSBuilder
import weco.pipeline.sierra_reader.config.builders.ReaderConfigBuilder
import weco.pipeline.sierra_reader.services.SierraReaderWorkerService
import weco.sierra.typesafe.SierraOauthHttpClientBuilder
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)

    implicit val s3Client: S3Client = S3Client.builder().build()
    implicit val s3TransferManager: S3TransferManager =
      S3TransferManager.builder().build()

    val client = SierraOauthHttpClientBuilder.build(config)

    new SierraReaderWorkerService(
      client = client,
      sqsStream = sqsStream,
      s3Config = S3Builder.buildS3Config(config),
      readerConfig = ReaderConfigBuilder.buildReaderConfig(config)
    )
  }
}
