package weco.pipeline.id_minter

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Json
import weco.typesafe.WellcomeTypesafeApp
import weco.messaging.typesafe.SNSBuilder
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.id_minter.config.builders.{
  IdentifiersTableBuilder,
  RDSBuilder
}
import weco.pipeline.id_minter.database.IdentifiersDao
import weco.pipeline.id_minter.models.IdentifiersTable
import weco.pipeline.id_minter.services.IdMinterWorkerService
import weco.pipeline.id_minter.steps.IdentifierGenerator
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val executionContext: ExecutionContext =
      ActorSystem("main-actor-system").dispatcher

    val identifiersTableConfig = IdentifiersTableBuilder.buildConfig(config)
    RDSBuilder.buildDB(config)

    val identifierGenerator = new IdentifierGenerator(
      identifiersDao = new IdentifiersDao(
        identifiers = new IdentifiersTable(
          identifiersTableConfig = identifiersTableConfig
        )
      )
    )

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workIndexer =
      new ElasticIndexer[Work[Identified]](
        client = esClient,
        index = Index(config.requireString("es.identified-works.index")),
        config = WorksIndexConfig.identified
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
      identifierGenerator = identifierGenerator,
      jsonRetriever = jsonRetriever,
      pipelineStream = pipelineStream,
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
