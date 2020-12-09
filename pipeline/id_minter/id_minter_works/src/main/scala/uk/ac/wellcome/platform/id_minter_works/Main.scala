package uk.ac.wellcome.platform.id_minter_works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.circe.Json
import uk.ac.wellcome.platform.id_minter.config.builders.{
  IdentifiersTableBuilder,
  RDSBuilder
}
import uk.ac.wellcome.platform.id_minter.database.IdentifiersDao
import uk.ac.wellcome.platform.id_minter.models.IdentifiersTable
import uk.ac.wellcome.platform.id_minter_works.services.IdMinterWorkerService
import uk.ac.wellcome.platform.id_minter.steps.IdentifierGenerator
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import WorkState.Identified

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

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

    val workIndexer = ElasticIndexerBuilder[Work[Identified]](
      config,
      esClient,
      namespace = "identified-works",
      indexConfig = IdentifiedWorkIndexConfig
    )

    val messageStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    val messageSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the id-minter")
    val pipelineStream =
      PipelineStorageStreamBuilder.buildPipelineStorageStream(
        messageStream,
        workIndexer,
        messageSender)(config)
    new IdMinterWorkerService(
      identifierGenerator = identifierGenerator,
      jsonRetriever =
        ElasticRetrieverBuilder[Json](config, esClient, namespace = "merged-works"),
      pipelineStream = pipelineStream,
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
