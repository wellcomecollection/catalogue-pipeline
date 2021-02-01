package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.elasticsearch.IndexedWorkIndexConfig
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import WorkState.{Denormalised, Indexed}

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val denormalisedWorkStream =
      SQSBuilder.buildSQSStream[NotificationMessage](config)

    val workRetriever = ElasticSourceRetrieverBuilder[Work[Denormalised]](
      config,
      client = ElasticBuilder
        .buildElasticClient(config, namespace = "pipeline_storage"),
      namespace = "denormalised-works")

    val workIndexer = ElasticIndexerBuilder[Work[Indexed]](
      config,
      ElasticBuilder.buildElasticClient(config, namespace = "catalogue"),
      namespace = "indexed-works",
      indexConfig = IndexedWorkIndexConfig,
      // The relation embedder will re-send an ID for a Work every time it adds
      // a new relation.  This is why we see way more IDs than there are Works on
      // the ingestor queue during a reindex.
      //
      // Depending on when the ingestor picks up those IDs, it might skip
      // the intermediate versions.  If so, we end up reindexing the same Work
      // repeatedly, which puts unnecessary pressure on Elasticsearch and
      // slows down the reindex.
      skipReindexingIdenticalDocuments = true
    )
    val messageSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the ingestor-works")
    val pipelineStream =
      PipelineStorageStreamBuilder.buildPipelineStorageStream(
        denormalisedWorkStream,
        workIndexer,
        messageSender)(config)

    new WorkIngestorWorkerService(
      pipelineStream = pipelineStream,
      workRetriever = workRetriever,
      transform = WorkTransformer.deriveData
    )
  }
}
