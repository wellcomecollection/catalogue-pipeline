package uk.ac.wellcome.platform.transformer.sierra

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.pipeline_storage.typesafe.ElasticSourceRetrieverBuilder
import uk.ac.wellcome.platform.transformer.sierra.services.SierraTransformerWorker
import weco.storage.store.s3.S3TypedStore
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.Work
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val esClient = ElasticBuilder.buildElasticClient(config)

    val pipelineStream = PipelineStorageStreamBuilder
      .buildPipelineStorageStream(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        indexer = ElasticIndexerBuilder[Work[Source]](
          config,
          esClient,
          indexConfig = WorksIndexConfig.source
        ),
        messageSender = SNSBuilder
          .buildSNSMessageSender(
            config,
            subject = "Sent from the Sierra transformer")
      )(config)

    implicit val s3Client: AmazonS3 =
      S3Builder.buildS3Client(config)

    new SierraTransformerWorker(
      pipelineStream = pipelineStream,
      sierraReadable = S3TypedStore[SierraTransformable],
      retriever =
        ElasticSourceRetrieverBuilder.apply[Work[Source]](config, esClient)
    )
  }
}
