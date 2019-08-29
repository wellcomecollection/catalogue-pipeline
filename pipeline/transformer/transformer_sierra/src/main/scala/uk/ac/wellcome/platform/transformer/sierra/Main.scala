package uk.ac.wellcome.platform.transformer.sierra

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext

import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.{
  HybridRecordReceiver,
  SierraTransformerWorkerService
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage

import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val msgStore =
      S3TypedStore[TransformedBaseWork]

    val messageReceiver = new HybridRecordReceiver(
      msgSender = BigMessagingBuilder
        .buildBigMessageSender[TransformedBaseWork](config),
      store = VHSBuilder
        .build[SierraTransformable](config))

    new SierraTransformerWorkerService(
      messageReceiver = messageReceiver,
      sierraTransformer = new SierraTransformableTransformer(),
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    )
  }
}
