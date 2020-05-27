package uk.ac.wellcome.platform.transformer.sierra

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.SierraTransformerWorkerService
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    import uk.ac.wellcome.platform.transformer.sierra.model.SierraTransformableImplicits._

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

    new SierraTransformerWorkerService(
      stream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      sender = BigMessagingBuilder
        .buildBigMessageSender[TransformedBaseWork](config),
      store = new VersionedStore(
        new VHSWrapper(
          VHSBuilder.build[SierraTransformable](config)
        )
      )
    )
  }
}
