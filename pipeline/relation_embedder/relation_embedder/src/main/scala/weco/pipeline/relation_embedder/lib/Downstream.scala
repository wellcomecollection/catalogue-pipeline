package weco.pipeline.relation_embedder.lib

import software.amazon.awssdk.services.sns.SnsClient
import weco.messaging.sns.{SNSConfig, SNSMessageSender}

import scala.util.Try

trait Downstream {
  def notify(workId: String): Try[Unit]
}

class SNSDownstream(snsConfig: SNSConfig) extends Downstream {
  private val msgSender = new SNSMessageSender(
    snsClient = SnsClient.builder().build(),
    snsConfig = snsConfig,
    subject = "Sent from relation_embedder"
  )

  override def notify(workId: String): Try[Unit] = Try(msgSender.send(workId))
}

object STDIODownstream extends Downstream {
  override def notify(workId: String): Try[Unit] = Try(println(workId))
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
