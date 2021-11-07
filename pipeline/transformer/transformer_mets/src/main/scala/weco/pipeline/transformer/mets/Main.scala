package weco.pipeline.transformer.mets

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.transformer.TransformerWorker
import weco.pipeline.transformer.mets.services.MetsSourceDataRetriever
import weco.pipeline.transformer.mets.transformer.MetsXmlTransformer
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.storage.store.s3.S3TypedStore
import weco.storage.typesafe.S3Builder
import weco.typesafe.config.builders.EnrichConfig._
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client

    val esClient = ElasticBuilder.buildElasticClient(config)

    val pipelineStream = PipelineStorageStreamBuilder
      .buildPipelineStorageStream(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        indexer =
          new ElasticIndexer[Work[Source]](
            client = esClient,
            index = Index(config.requireString("es.index")),
            config = WorksIndexConfig.source
          ),
        messageSender = SNSBuilder
          .buildSNSMessageSender(
            config,
            subject = "Sent from the METS transformer")
      )(config)

    val retriever =
      new ElasticSourceRetriever[Work[Source]](
        client = esClient,
        index = Index(config.requireString("es.index"))
      )

    new TransformerWorker(
      transformer = new MetsXmlTransformer(S3TypedStore[String]),
      pipelineStream = pipelineStream,
      retriever = retriever,
      sourceDataRetriever = new MetsSourceDataRetriever
    )
  }
}
