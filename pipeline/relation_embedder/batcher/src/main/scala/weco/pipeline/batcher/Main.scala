package weco.pipeline.batcher

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.actor.ActorSystem
import com.typesafe.config.Config

import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    new BatcherWorkerService(
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender =
        SNSBuilder.buildSNSMessageSender(config, subject = "Sent from batcher"),
      flushInterval =
        config.requireInt("batcher.flush_interval_minutes").minutes,
      maxProcessedPaths = config.requireInt("batcher.max_processed_paths"),
      maxBatchSize = config.requireInt("batcher.max_batch_size")
    )
  }
}
