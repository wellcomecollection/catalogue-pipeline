package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import weco.lambda._
import weco.pipeline.batcher.lib.{BatcherConfig, BatcherConfigurable}

import scala.concurrent.Future

object LambdaMain
    extends LambdaApp[SQSEvent, String, BatcherConfig]
    with BatcherConfigurable {

  import weco.lambda.SQSEventOps._

  private val pathsProcessor = PathsProcessor(
    Downstream(config.downstreamTarget),
    config.maxBatchSize
  )

  override def processEvent(in: SQSEvent): Future[String] = {
    debug(s"Running batcher lambda, got event: $in")
    pathsProcessor(in.extract[String]).map(_ => "Done")
  }
}
