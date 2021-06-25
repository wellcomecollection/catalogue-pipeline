package weco.pipeline_storage.typesafe

import com.typesafe.config.Config
import weco.messaging.MessageSender
import weco.messaging.sqs.SQSStream
import weco.pipeline_storage.{
  Indexer,
  PipelineStorageConfig,
  PipelineStorageStream
}
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline_storage.{
  Indexer,
  PipelineStorageConfig,
  PipelineStorageStream
}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object PipelineStorageStreamBuilder {
  def buildPipelineStorageConfig(config: Config): PipelineStorageConfig =
    PipelineStorageConfig(
      batchSize = config.requireInt("pipeline_storage.batch_size"),
      flushInterval =
        config.requireInt("pipeline_storage.flush_interval_seconds").seconds,
      parallelism =
        config.getIntOption("pipeline_storage.parallelism").getOrElse(10)
    )

  def buildPipelineStorageStream[In, Out, MsgDestination](
    sqsStream: SQSStream[In],
    indexer: Indexer[Out],
    messageSender: MessageSender[MsgDestination])(config: Config)(
    implicit ec: ExecutionContext)
    : PipelineStorageStream[In, Out, MsgDestination] =
    new PipelineStorageStream[In, Out, MsgDestination](
      sqsStream,
      indexer,
      messageSender)(buildPipelineStorageConfig(config))
}
