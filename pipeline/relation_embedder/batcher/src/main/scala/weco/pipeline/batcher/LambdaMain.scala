package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import org.apache.pekko.actor.ActorSystem
import weco.messaging.typesafe.SNSBuilder
import weco.json.JsonUtil._
import com.typesafe.config.ConfigFactory
import weco.typesafe.config.builders.EnrichConfig.RichConfig

import scala.concurrent.ExecutionContext
import scala.util.Try

object LambdaMain extends RequestHandler[SQSEvent, String] with Logging {
  import weco.pipeline.batcher.lib.SQSEventOps._

  private val config = ConfigFactory.load("application")

  private val downstream = config.getString("batcher.use_downstream") match {
    case "sns"   => SNSDownstream
    case "stdio" => STDIODownstream
  }

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): String = {
    info(s"running batcher lambda, got event: $event")

    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")
    implicit val ec: ExecutionContext =
      actorSystem.dispatcher

    PathsProcessor(
      config.requireInt("batcher.max_batch_size"),
      event.extractPaths,
      downstream
    )
    "Done"
  }

  private object SNSDownstream extends Downstream {
    private val msgSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from batcher")
    override def notify(batch: Batch): Try[Unit] = msgSender.sendT(batch)
  }
}
