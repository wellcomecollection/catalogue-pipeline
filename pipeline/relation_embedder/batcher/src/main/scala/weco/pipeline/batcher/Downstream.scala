package weco.pipeline.batcher

import scala.util.Try

trait Downstream {
  def notify(batch: Batch): Try[Unit]
}

object STDIODownstream extends Downstream {
  override def notify(batch: Batch): Try[Unit] = Try(println(batch))
}
