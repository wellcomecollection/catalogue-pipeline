package uk.ac.wellcome.platform.idminter

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import io.circe.Json
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.config.builders.{IdentifiersTableBuilder, RDSBuilder}
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.platform.idminter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.idminter.steps.{IdEmbedder, IdentifierGenerator}
import uk.ac.wellcome.platform.idminter.utils.SimpleDynamoStore
import uk.ac.wellcome.storage.dynamo.DynamoConfig
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, S3Builder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

import org.scanamo.auto._


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

    val identifiersTableConfig = IdentifiersTableBuilder.buildConfig(config)

    val dynamoStore = new SimpleDynamoStore[SourceIdentifier, Identifier](dynamoConfig)

    val identifierGenerator = new IdentifierGenerator(
      identifiersDao = new IdentifiersDao(
        db = RDSBuilder.buildDB(config),
        identifiers = new IdentifiersTable(
          identifiersTableConfig = identifiersTableConfig
        ),
        dynamoStore
      )
    )

    val idEmbedder = new IdEmbedder(
      identifierGenerator = identifierGenerator
    )

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)
    implicit val s3TypedStore = S3TypedStore[Json]

    new IdMinterWorkerService(
      idEmbedder = idEmbedder,
      sender = BigMessagingBuilder.buildBigMessageSender[Json](config),
      messageStream = BigMessagingBuilder.buildMessageStream[Json](config),
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
