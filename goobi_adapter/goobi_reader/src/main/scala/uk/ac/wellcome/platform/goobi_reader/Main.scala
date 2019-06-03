package uk.ac.wellcome.platform.goobi_reader

import java.io.InputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.platform.goobi_reader.models.GoobiRecordMetadata
import uk.ac.wellcome.platform.goobi_reader.services.GoobiReaderWorkerService
import uk.ac.wellcome.storage.{ObjectStore, StorageBackend}
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.s3.S3StorageBackend
import uk.ac.wellcome.storage.streaming.Codec
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.typesafe.{S3Builder, VHSBuilder}
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

    val s3ObjectStore = new ObjectStore[InputStream] {
      override implicit val codec: Codec[InputStream] = streamCodec
      override implicit val storageBackend: StorageBackend = new S3StorageBackend(
        s3Client = S3Builder.buildS3Client(config)
      )
    }

    new GoobiReaderWorkerService(
      s3ObjectStore = s3ObjectStore,
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      vhs = VHSBuilder.buildVHS[String, InputStream, GoobiRecordMetadata](config)
    )
  }
}
