package weco.pipeline.sierra_reader

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
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

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client

    val client = SierraOauthHttpClientBuilder.build(config)

    new SierraReaderWorkerService(
      client = client,
      sqsStream = sqsStream,
      s3Config = S3Builder.buildS3Config(config),
      readerConfig = ReaderConfigBuilder.buildReaderConfig(config)
    )
  }
}
