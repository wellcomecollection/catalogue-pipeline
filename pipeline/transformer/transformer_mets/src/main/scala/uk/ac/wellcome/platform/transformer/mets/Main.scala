package uk.ac.wellcome.platform.transformer.mets

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.transformer.mets.services.MetsTransformerWorker
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.elasticsearch.SourceWorkIndexConfig
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.config.builders.AWSClientConfigBuilder

object Main extends WellcomeTypesafeApp with AWSClientConfigBuilder {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

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
            subject = "Sent from the METS transformer")
      )(config)

    new MetsTransformerWorker(
      pipelineStream = pipelineStream,
      metsXmlStore = S3TypedStore[String],
      retriever =
        ElasticSourceRetrieverBuilder.apply[Work[Source]](config, esClient)
    )
  }
}
