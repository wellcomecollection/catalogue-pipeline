package uk.ac.wellcome.platform.transformer.calm

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.models.Implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.{
  AWSClientConfigBuilder,
  AkkaBuilder
}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp with AWSClientConfigBuilder {

  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val msgStore =
      S3TypedStore[TransformedBaseWork]

    implicit val dec: Decoder[CalmRecord] = deriveDecoder
    implicit val enc: Encoder[CalmRecord] = deriveEncoder

    val stream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    val sender =
      BigMessagingBuilder.buildBigMessageSender[TransformedBaseWork](config)
    val store = new VersionedStore(
      new VHSWrapper(
        VHSBuilder.build[CalmRecord](config)
      )
    )

    new CalmTransformerWorker(stream, sender, store)
  }
}
