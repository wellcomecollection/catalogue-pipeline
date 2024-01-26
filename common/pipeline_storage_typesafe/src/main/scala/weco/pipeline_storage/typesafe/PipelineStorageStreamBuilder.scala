package weco.pipeline_storage.typesafe

import akka.actor.ActorSystem
import com.typesafe.config.Config
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.SQSBuilder
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline_storage.{Indexer, PipelineStorageConfig, PipelineStorageStream}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object PipelineStorageStreamBuilder {
  def buildPipelineStorageConfig(config: Config): PipelineStorageConfig =
    PipelineStorageConfig(
      batchSize = config.requireInt("pipeline_storage.batch_size"),
      flushInterval = config.requireInt("pipeline_storage.flush_interval_seconds").seconds,
      parallelism = config.getIntOption("pipeline_storage.parallelism").getOrElse(10)
    )

  def buildPipelineStorageStream[Out, MsgDestination](
    indexer: Indexer[Out],
    messageSender: MessageSender[MsgDestination]
  )(config: Config)(
    implicit ec: ExecutionContext
  ): PipelineStorageStream[NotificationMessage, Out, MsgDestination] = {
    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")

    val messageStream = SQSBuilder.buildSQSStream[NotificationMessage](config)

    new PipelineStorageStream[NotificationMessage, Out, MsgDestination](
      messageStream,
      indexer,
      messageSender
    )(buildPipelineStorageConfig(config))
  }
}
