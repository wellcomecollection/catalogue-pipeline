package weco.lambda.helpers

import io.circe.Encoder
import weco.lambda.Downstream
import weco.messaging.memory.MemoryMessageSender

import scala.util.Try

trait MemoryDownstream {

  class MemorySNSDownstream(sender: MemoryMessageSender = new MemoryMessageSender)
    extends Downstream {

    val msgSender: MemoryMessageSender = sender

    override def notify(workId: String): Try[Unit] =
      Try(msgSender.send(workId))
    override def notify[T](batch: T)(implicit encoder: Encoder[T]): Try[Unit] =
      Try(msgSender.send(encoder.apply(batch).toString()))
  }
}
