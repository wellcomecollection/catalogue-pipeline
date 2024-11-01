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

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): String = {
    info(s"running batcher lambda, got event: $event")

    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")
    implicit val ec: ExecutionContext =
      actorSystem.dispatcher

    val recordList: List[SQSMessage] = event.getRecords.asScala.toList
    val paths = recordList map {
      message: SQSMessage =>
        // This assumes that the input is message body is just the path
        // not wrapped in any other JSON.
        // This may need to be altered depending on how the message is formatted
        // in real life
        ujson.read(message.getBody).str
    }
    PathsProcessor(
      config.requireInt("batcher.max_batch_size"),
      paths,
      SNSDownstream
    )
    "Done"
  }

  private object SNSDownstream extends Downstream {
    private val msgSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from batcher")
    override def notify(batch: Batch): Try[Unit] = msgSender.sendT(batch)
  }
}
