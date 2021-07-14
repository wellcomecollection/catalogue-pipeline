package weco.pipeline.transformer.calm

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.work.WorkState.Source
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.pipeline_storage.typesafe.ElasticSourceRetrieverBuilder
import weco.storage.store.s3.S3TypedStore
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.{AWSClientConfigBuilder, AkkaBuilder}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.services.CalmTransformerWorker
import weco.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp with AWSClientConfigBuilder {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val ec: ExecutionContext =
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
            subject = "Sent from the CALM transformer")
      )(config)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client

    new CalmTransformerWorker(
      pipelineStream = pipelineStream,
      recordReadable = S3TypedStore[CalmRecord],
      retriever =
        ElasticSourceRetrieverBuilder.apply[Work[Source]](config, esClient)
    )
  }
}
