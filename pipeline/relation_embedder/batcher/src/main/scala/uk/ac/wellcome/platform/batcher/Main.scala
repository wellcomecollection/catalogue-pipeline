package uk.ac.wellcome.platform.batcher

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()

    new BatcherWorkerService(
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender = SNSBuilder.buildSNSMessageSender(config, subject = "Sent from batcher"),
      flushInterval = config.requireInt("batcher.flush_interval_minutes").minutes,
      maxProcessedPaths = config.requireInt("batcher.max_processed_paths"),
      maxBatchSize = config.requireInt("batcher.max_batch_size")
    )
  }
}
