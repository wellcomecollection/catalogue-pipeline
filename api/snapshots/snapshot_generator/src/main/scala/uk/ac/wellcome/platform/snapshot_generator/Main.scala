package uk.ac.wellcome.platform.snapshot_generator

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.display.ElasticConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.snapshot_generator.config.builders.AkkaS3Builder
import uk.ac.wellcome.platform.snapshot_generator.models.SnapshotGeneratorConfig
import uk.ac.wellcome.platform.snapshot_generator.services.{
  SnapshotGeneratorWorkerService,
  SnapshotService
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val snapshotConfig = SnapshotGeneratorConfig(
      index = ElasticConfig().worksIndex,
      bulkSize = config.getIntOption("es.bulk-size").getOrElse(1000)
    )

    val snapshotService = new SnapshotService(
      akkaS3Settings = AkkaS3Builder.buildAkkaS3Settings(config),
      elasticClient = ElasticBuilder.buildElasticClient(config),
      snapshotConfig = snapshotConfig
    )

    new SnapshotGeneratorWorkerService(
      snapshotService = snapshotService,
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      messageSender = SNSBuilder.buildSNSMessageSender(
        config,
        subject = s"source: ${this.getClass.getSimpleName}.processMessage",
      )
    )
  }
}
