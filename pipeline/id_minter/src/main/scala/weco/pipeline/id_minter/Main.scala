package weco.pipeline.id_minter

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.ExecutionContext
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Json
import weco.typesafe.WellcomeTypesafeApp
import weco.messaging.typesafe.SNSBuilder
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.id_minter.config.builders.IdentifiersTableBuilder
import weco.pipeline.id_minter.config.models.RDSClientConfig
import weco.pipeline.id_minter.services.IdMinterWorkerService
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val executionContext: ExecutionContext =
        ActorSystem("main-actor-system").dispatcher
      val rdsConfig = RDSClientConfig(config)
      val identifiersTableConfig = IdentifiersTableBuilder.buildConfig(config)

      val esClient = ElasticBuilder.buildElasticClient(config)

      val workIndexer =
        new ElasticIndexer[Work[Identified]](
          client = esClient,
          index = Index(config.requireString("es.identified-works.index"))
        )

      val messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the id-minter")

      val pipelineStream =
        PipelineStorageStreamBuilder
          .buildPipelineStorageStream(workIndexer, messageSender)(config)

      val jsonRetriever =
        new ElasticSourceRetriever[Json](
          client = esClient,
          index = Index(config.requireString("es.source-works.index"))
        )

      new IdMinterWorkerService(
        maybeIdentifierGenerator = None,
        jsonRetriever = jsonRetriever,
        pipelineStream = pipelineStream,
        rdsClientConfig = rdsConfig,
        identifiersTableConfig = identifiersTableConfig
      )
  }
}
