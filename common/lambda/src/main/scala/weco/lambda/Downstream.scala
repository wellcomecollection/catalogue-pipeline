package weco.lambda

import io.circe.Encoder
import software.amazon.awssdk.services.sns.SnsClient
import weco.messaging.sns.{SNSConfig, SNSMessageSender}
import weco.json.JsonUtil.toJson

import scala.util.Try

trait Downstream {
  def notify(workId: String): Try[Unit]
  def notify[T](batch: T)(implicit encoder: Encoder[T]): Try[Unit]
}

class SNSDownstream(snsConfig: SNSConfig) extends Downstream {
  protected val msgSender = new SNSMessageSender(
    snsClient = SnsClient.builder().build(),
    snsConfig = snsConfig,
    subject = "Sent from relation_embedder"
  )

  override def notify(workId: String): Try[Unit] = Try(msgSender.send(workId))
  override def notify[T](batch: T)(implicit encoder: Encoder[T]): Try[Unit] =
    msgSender.sendT(batch)
}

object STDIODownstream extends Downstream {
  override def notify(workId: String): Try[Unit] = Try(println(workId))
  override def notify[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] = Try(
    println(toJson(t))
  )
}

sealed trait DownstreamTarget
case class SNS(config: SNSConfig) extends DownstreamTarget
case object StdOut extends DownstreamTarget

object Downstream {
  def apply(downstreamTarget: DownstreamTarget): Downstream = {
    downstreamTarget match {
      case SNS(config) => new SNSDownstream(config)
      case StdOut      => STDIODownstream
    }
  }
  def apply(): Downstream = STDIODownstream
}
