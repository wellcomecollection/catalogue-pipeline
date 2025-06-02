package weco.lambda.helpers

import io.circe.Encoder
import weco.lambda.Downstream
import weco.messaging.memory.MemoryMessageSender

import scala.util.Try

trait DownstreamHelper {

  class MemoryDownstream extends Downstream {
    val msgSender = new MemoryMessageSender

    override def notify(workId: String): Try[Unit] =
      Try(msgSender.send(workId))
    override def notify[T](batch: T)(implicit encoder: Encoder[T]): Try[Unit] =
      Try(msgSender.send(encoder.apply(batch).toString()))
  }
}
