package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.apache.pekko.actor.ActorSystem
import weco.messaging.typesafe.SNSBuilder
import weco.json.JsonUtil._
import com.typesafe.config.ConfigFactory
import weco.typesafe.config.builders.EnrichConfig.RichConfig

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.Try

object LambdaMain extends RequestHandler[SQSEvent, String] with Logging {
  private val config = ConfigFactory.load("application")

  private val downstream = config.getString("batcher.use_downstream") match {
    case "sns"   => SNSDownstream
    case "stdio" => STDIODownstream
  }

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): String = {
    debug(s"Running batcher lambda, got event: $event")

    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")
    implicit val ec: ExecutionContext =
      actorSystem.dispatcher

    PathsProcessor(
      config.requireInt("batcher.max_batch_size"),
      extractPathsFromEvent(event),
      downstream
    )

    "Done"
  }

  /** Messages consumed by this Lambda are taken from a queue populated by an
    * SNS topic. The actual message we are interested in is a String containing
    * the path. However, the matryoshka-like nature of these things means the
    * lambda receives
    *   - an event containing
    *   - a `Records` list, each Record containing
    *   - an SQS Message with a JSON body containing
    *   - an SNS notification containing
    *   - a `Message`, which is the actual content we want
    */
  private def extractPathsFromEvent(event: SQSEvent): List[String] =
    event.getRecords.asScala.toList.flatMap(extractPathFromMessage)

  private def extractPathFromMessage(message: SQSMessage): Option[String] =
    ujson.read(message.getBody).obj.get("Message").map(_.toString)

  private object SNSDownstream extends Downstream {
    private val msgSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from batcher")
    override def notify(batch: Batch): Try[Unit] = msgSender.sendT(batch)
  }
}
