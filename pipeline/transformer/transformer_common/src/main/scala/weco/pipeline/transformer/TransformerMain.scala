package weco.pipeline.transformer

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Decoder
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.source_model.SourcePayload
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SNSBuilder
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.Runnable
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

class TransformerMain[Payload <: SourcePayload, SourceData](
  sourceName: String,
  transformer: Transformer[SourceData],
  sourceDataRetriever: SourceDataRetriever[Payload, SourceData]
)(implicit decoder: Decoder[Payload]) {
  def run(config: Config): Runnable = {
    implicit val ec: ExecutionContext =
      ActorSystem("main-actor-system").dispatcher

    val esClient = ElasticBuilder.buildElasticClient(config)

    val sourceWorkIndex = Index(config.requireString("es.index"))

    val sourceWorkIndexer =
      new ElasticIndexer[Work[Source]](
        client = esClient,
        index = sourceWorkIndex,
        config = WorksIndexConfig.source
      )

    val sourceWorkRetriever =
      new ElasticSourceRetriever[Work[Source]](
        client = esClient,
        index = sourceWorkIndex
      )

    val messageSender =
      SNSBuilder
        .buildSNSMessageSender(
          config,
          subject = s"Sent from the $sourceName transformer"
        )

    val pipelineStream = PipelineStorageStreamBuilder
      .buildPipelineStorageStream(sourceWorkIndexer, messageSender)(config)

    new TransformerWorker(
      transformer = transformer,
      pipelineStream = pipelineStream,
      retriever = sourceWorkRetriever,
      sourceDataRetriever = sourceDataRetriever
    )
  }
}
