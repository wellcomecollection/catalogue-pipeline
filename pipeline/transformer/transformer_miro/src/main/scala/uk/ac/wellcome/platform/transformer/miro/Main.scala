package uk.ac.wellcome.platform.transformer.miro

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.services.{
  MiroTransformerWorkerService,
  MiroVHSRecordReceiver
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.transformer.miro.Implicits._

import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.messaging.sns.SNSConfig

import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.storage.streaming.Codec._

object Main extends WellcomeTypesafeApp {

  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val msgStore =
      S3TypedStore[TransformedBaseWork]

    val vhsRecordReceiver = new MiroVHSRecordReceiver[SNSConfig](
      msgSender = BigMessagingBuilder
        .buildBigMessageSender[TransformedBaseWork](config),
      store = S3TypedStore[MiroRecord]
    )

    new MiroTransformerWorkerService(
      vhsRecordReceiver = vhsRecordReceiver,
      miroTransformer = new MiroRecordTransformer,
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    )
  }
}
