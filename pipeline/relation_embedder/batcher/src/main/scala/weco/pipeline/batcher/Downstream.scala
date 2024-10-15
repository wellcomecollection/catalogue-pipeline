package weco.pipeline.batcher

import scala.util.Try

trait Downstream {
  def notify(batch: Batch): Try[Unit]
}
