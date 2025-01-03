package weco.pipeline.relation_embedder

import com.typesafe.config.Config
import weco.messaging.typesafe.SNSBuilder

import scala.util.Try

trait Downstream {
  def notify(workId: String): Try[Unit]
}

class SNSDownstream(config: Config) extends Downstream {
  private val msgSender = SNSBuilder
    .buildSNSMessageSender(config, subject = "Sent from relation_embedder")

  override def notify(workId: String): Try[Unit] = Try(msgSender.send(workId))
}

object STDIODownstream extends Downstream {
  override def notify(workId: String): Try[Unit] = Try(println(workId))
}

object Downstream {
  def apply(maybeConfig: Option[Config]): Downstream = {
    maybeConfig match {
      case Some(config) =>
        config.getString("relation_embedder.use_downstream") match {
          case "sns"   => new SNSDownstream(config)
          case "stdio" => STDIODownstream
        }
      case None => STDIODownstream
    }
  }
}
