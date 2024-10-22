package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.apache.pekko.actor.ActorSystem

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.Try

object LambdaMain extends RequestHandler[SQSEvent, String] with Logging {

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
    val paths = recordList flatMap {
      message: SQSMessage =>
        ujson.read(message.getBody).obj.get("Message").toList
    } map (_.str)
    PathsProcessor(
      40, // TODO: 40 is the number in the config used by Main, do this properly later
      paths,
      STDIODownstream
    )
    "Done"
  }

  private object STDIODownstream extends Downstream {
    override def notify(batch: Batch): Try[Unit] = Try(println(batch))
  }
}
