package uk.ac.wellcome.platform.transformer.sierra

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.elasticsearch.SourceWorkIndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import uk.ac.wellcome.platform.transformer.sierra.services.SierraTransformerWorkerService
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    import uk.ac.wellcome.sierra_adapter.model.Implicits._

    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val indexer = new ElasticIndexer[Work[Source]](
      client = ElasticBuilder.buildElasticClient(config),
      index = Index(config.requireString("es.index")),
      config = SourceWorkIndexConfig
    )

    val pipelineStream = PipelineStorageStreamBuilder.buildPipelineStorageStream(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      indexer = indexer,
      messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "Sent from the METS transformer")
    )(config)

    new SierraTransformerWorkerService(
      pipelineStream = pipelineStream,
      store = VHSBuilder.build[SierraTransformable](config)
    )
  }
}
