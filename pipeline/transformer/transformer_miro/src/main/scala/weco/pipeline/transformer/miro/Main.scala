package weco.pipeline.transformer.miro

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
import Implicits._
import weco.storage.store.s3.S3TypedStore
import weco.storage.streaming.Codec._
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.Work
import weco.pipeline.transformer.miro.services.MiroTransformerWorker
import weco.pipeline.transformer.miro.source.MiroRecord
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

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client

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
            subject = "Sent from the Miro transformer")
      )(config)

    new MiroTransformerWorker(
      pipelineStream = pipelineStream,
      miroReadable = S3TypedStore[MiroRecord],
      retriever =
        ElasticSourceRetrieverBuilder.apply[Work[Source]](config, esClient)
    )
  }
}
