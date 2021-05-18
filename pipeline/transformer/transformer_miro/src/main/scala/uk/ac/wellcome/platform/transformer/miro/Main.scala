package uk.ac.wellcome.platform.transformer.miro

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import uk.ac.wellcome.models.index.SourceWorkIndexConfig
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.work.WorkState.Source
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.platform.transformer.miro.Implicits._
import uk.ac.wellcome.platform.transformer.miro.services.MiroTransformerWorker
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.Identified
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.Work

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    implicit val s3Client: AmazonS3 =
      S3Builder.buildS3Client(config)

    val esClient = ElasticBuilder.buildElasticClient(config)

    val pipelineStream = PipelineStorageStreamBuilder
      .buildPipelineStorageStream(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        indexer = ElasticIndexerBuilder[Work[Source]](
          config,
          esClient,
          indexConfig = SourceWorkIndexConfig
        ),
        messageSender = SNSBuilder
          .buildSNSMessageSender(
            config,
            subject = "Sent from the Miro transformer")
      )(config)

    new MiroTransformerWorker(
      pipelineStream = pipelineStream,
      miroReadable = S3TypedStore[MiroRecord],
      retriever =
        ElasticSourceRetrieverBuilder.apply[Work[Source]](config, esClient),
      licenseOverrides = getLicenseOverrides(config)
    )
  }

  private def getLicenseOverrides(config: Config)(
    implicit s3Client: AmazonS3): Map[String, License] = {
    val location = S3ObjectLocation(
      bucket = config.requireString("miro.licenseOverride.bucket"),
      key = config.requireString("miro.licenseOverride.key")
    )

    val typedStore = S3TypedStore[Map[String, License]]
    typedStore.get(location) match {
      case Right(Identified(_, value)) => value
      case Left(err) =>
        throw new RuntimeException(s"Unable to load license override: $err")
    }
  }
}
