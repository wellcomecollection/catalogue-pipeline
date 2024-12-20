package weco.pipeline.batcher

import weco.lambda._
import weco.pipeline.batcher.lib.{BatcherConfig, BatcherConfigurable}

import scala.concurrent.Future

object LambdaMain
  extends SQSLambdaApp[String, String, BatcherConfig]
    with BatcherConfigurable {

  private val pathsProcessor = PathsProcessor(
    Downstream(config.downstreamTarget),
    config.maxBatchSize
  )

  override def processT(t: List[String]): Future[String] =
    pathsProcessor(t).map(_ => "Done")
}
