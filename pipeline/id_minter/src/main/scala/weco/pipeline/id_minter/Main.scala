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
import weco.pipeline.id_minter.config.builders.{IdentifiersTableBuilder, RDSBuilder}
import weco.pipeline.id_minter.database.IdentifiersDao
import weco.pipeline.id_minter.models.IdentifiersTable
import weco.pipeline.id_minter.services.{CommandLineIdMinterWorkerService, IdMinterWorkerService}
import weco.pipeline.id_minter.steps.IdentifierGenerator
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  // read and print args passed from the command line
  val runAsCli = args.length > 0
  val idToProcess = if (runAsCli) Some(args(0)) else None

  println(s"runAsCli: $runAsCli")
  println(s"idToProcess: $idToProcess")

  runWithConfig {
    config: Config =>
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

      if(runAsCli) {
        new CommandLineIdMinterWorkerService(
          identifierGenerator = identifierGenerator,
          jsonRetriever = jsonRetriever
        )(idToProcess.get)
      } else {
        new IdMinterWorkerService(
          identifierGenerator = identifierGenerator,
          jsonRetriever = jsonRetriever,
          pipelineStream = pipelineStream,
          rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
          identifiersTableConfig = identifiersTableConfig
        )
      }
  }
}
