package uk.ac.wellcome.platform.sierra_reader

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.platform.sierra_reader.config.builders.{
  ReaderConfigBuilder,
  SierraAPIConfigBuilder
}
import uk.ac.wellcome.platform.sierra_reader.services.SierraReaderWorkerService
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.http.client.sierra.SierraOauthHttpClient
import weco.http.client.{AkkaHttpClient, HttpGet, HttpPost}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    val apiConfig = SierraAPIConfigBuilder.buildSierraConfig(config)

    val client = new SierraOauthHttpClient(
      underlying = new AkkaHttpClient() with HttpPost with HttpGet {
        override val baseUri: Uri = Uri(apiConfig.apiURL)
      },
      credentials =
        new BasicHttpCredentials(apiConfig.oauthKey, apiConfig.oauthSec)
    )

    new SierraReaderWorkerService(
      client = client,
      sqsStream = sqsStream,
      s3Config = S3Builder.buildS3Config(config),
      readerConfig = ReaderConfigBuilder.buildReaderConfig(config)
    )
  }
}
