package weco.pipeline.batcher

import grizzled.slf4j.Logging
import weco.lambda._
import weco.pipeline.batcher.lib.{BatcherConfig, BatcherConfigurable}


object Main
  extends BatcherSqsLambda[BatcherConfig]
    with BatcherConfigurable
    with Logging {


  override val processor: PathsProcessor = PathsProcessor(config.maxBatchSize)

  override val downstream: Downstream = Downstream(config.downstreamTarget)
}
