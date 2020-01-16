package uk.ac.wellcome.platform.idminter

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import io.circe.Json
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.platform.idminter.services.{IdMinterWorkerService, IdentifiersService}
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
import uk.ac.wellcome.platform.idminter.utils.DynamoIdentifierStore
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext
import org.scanamo.auto._
import uk.ac.wellcome.platform.idminter.utils.DynamoFormats._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>

    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)
    implicit val dynamoConfig: DynamoConfig =
      DynamoBuilder.buildDynamoConfig(config)

    val dynamoStore = new DynamoIdentifierStore(dynamoConfig)
    val identifiersDao = new IdentifiersService(dynamoStore)
    val identifierGenerator = new IdentifierGenerator(identifiersDao)
    val idEmbedder = new IdEmbedder(identifierGenerator)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)
    implicit val s3TypedStore = S3TypedStore[Json]

    new IdMinterWorkerService(
      idEmbedder = idEmbedder,
      sender = BigMessagingBuilder.buildBigMessageSender[Json](config),
      messageStream = BigMessagingBuilder.buildMessageStream[Json](config)
    )
  }
}
