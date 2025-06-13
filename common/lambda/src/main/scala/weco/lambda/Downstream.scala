package weco.lambda

import com.typesafe.config.Config
import grizzled.slf4j.Logging
import io.circe.Encoder
import software.amazon.awssdk.services.sns.SnsClient
import weco.messaging.sns.{SNSConfig, SNSMessageSender}
import weco.json.JsonUtil.toJson
import weco.messaging.MessageSender
import weco.messaging.typesafe.SNSBuilder.buildSNSConfig

import scala.util.Try

trait Downstream {
  def notify(workId: String): Try[Unit]
  def notify[T](batch: T)(implicit encoder: Encoder[T]): Try[Unit]
}

class SNSDownstream(snsConfig: SNSConfig) extends Downstream {
  protected val msgSender: MessageSender[_] = new SNSMessageSender(
    snsClient = SnsClient.builder().build(),
    snsConfig = snsConfig,
    // format the topicArn to keep the service name only
    subject = s"Sent from ${snsConfig.topicArn.split(":").last.split("_").drop(1).dropRight(1).mkString("-")}"
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

// Typesafe specific configuration builder
object DownstreamBuilder extends Logging {
  import weco.typesafe.config.builders.EnrichConfig._

  def buildDownstreamTarget(config: Config, namespace: String = ""): DownstreamTarget = {
    config.getStringOption("downstream.target") match {
      case Some("sns") =>
        val snsConfig = buildSNSConfig(config, namespace)
        info(s"Building SNS downstream with config: $snsConfig")
        SNS(snsConfig)
      case Some("stdio") =>
        info("Building StdOut downstream")
        StdOut
      case Some(unknownTarget) =>
        throw new IllegalArgumentException(
          s"Invalid downstream target: $unknownTarget"
        )
      case None =>
        warn("No downstream target specified, defaulting to StdOut")
        StdOut
    }
  }
}
