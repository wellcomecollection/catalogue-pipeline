package uk.ac.wellcome.platform.sierra_items_to_dynamo

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.{
  VHSInserter,
  SierraItemsToDynamoWorkerService
}
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.typesafe.VHSBuilder
import uk.ac.wellcome.storage.vhs.EmptyMetadata
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    val dynamoInserter = new VHSInserter(
      vhs = VHSBuilder.buildVHS[String, SierraItemRecord, EmptyMetadata](config)
    )

    new SierraItemsToDynamoWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      vhsInserter = dynamoInserter,
      messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "Sent from sierra_items_to_dynamo")
    )
  }
}
