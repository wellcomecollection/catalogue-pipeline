package uk.ac.wellcome.platform.sierra_items_to_dynamo

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.{DynamoInserter, SierraItemsToDynamoWorkerService}
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val msgStore = S3TypedStore[SierraItemRecord]
    val dynamoInserter = new DynamoInserter(new VersionedStore(
      new VHSWrapper(
        VHSBuilder.build[SierraItemRecord](config)))
    )

    new SierraItemsToDynamoWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      dynamoInserter = dynamoInserter,
      messageSender = BigMessagingBuilder.buildBigMessageSender(config)
    )
  }
}
