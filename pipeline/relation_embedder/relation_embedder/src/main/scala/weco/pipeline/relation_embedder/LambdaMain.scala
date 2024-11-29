package weco.pipeline.relation_embedder

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object LambdaMain extends RequestHandler[SQSEvent, String] with Logging {
  import weco.pipeline.relation_embedder.lib.SQSEventOps._

  private val config = ConfigFactory.load("application")
  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): String = {
    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")
    implicit val ec: ExecutionContext =
      actorSystem.dispatcher
    val batchProcessor = BatchProcessor(config)

    info(s"running relation_embedder lambda, got event: $event")
    // Wait here so that lambda can finish executing correctly.
    // 15 minutes is the maximum time allowed for a lambda to run.
    Await.result(
      Future.sequence(event.extractBatches.map(batchProcessor(_))),
      15.minutes
    )
    "Done"
  }
}
