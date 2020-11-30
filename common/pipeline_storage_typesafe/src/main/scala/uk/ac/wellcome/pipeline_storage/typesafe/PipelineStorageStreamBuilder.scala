package uk.ac.wellcome.pipeline_storage.typesafe

import com.typesafe.config.Config
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.{
  Indexer,
  PipelineStorageConfig,
  PipelineStorageStream
}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
object PipelineStorageStreamBuilder {

  def buildPipelineStorageConfig(config: Config): PipelineStorageConfig = {
    PipelineStorageConfig(
      config.requireInt("pipeline_storage.batch_size"),
      config.requireInt("pipeline_storage.flush_interval_seconds").seconds,
      config.getIntOption("pipeline_storage.parallelism").getOrElse(10)
    )
  }

  def buildPipelineStorageStream[In, Out, MsgDestination](
    sqsStream: SQSStream[In],
    indexer: Indexer[Out],
    messageSender: MessageSender[MsgDestination])(config: Config)(
    implicit ec: ExecutionContext)
    : PipelineStorageStream[In, Out, MsgDestination] = {
    new PipelineStorageStream[In, Out, MsgDestination](
      sqsStream,
      indexer,
      messageSender)(buildPipelineStorageConfig(config))
  }
}
