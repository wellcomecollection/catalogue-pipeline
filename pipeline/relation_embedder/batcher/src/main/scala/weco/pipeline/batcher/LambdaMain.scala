package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import weco.messaging.typesafe.SNSBuilder
import weco.json.JsonUtil._
import com.typesafe.config.ConfigFactory
import weco.typesafe.config.builders.EnrichConfig.RichConfig
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext
import scala.util.Try
import ExecutionContext.Implicits.global

object LambdaMain extends RequestHandler[SQSEvent, String] with Logging {
  import weco.pipeline.batcher.lib.SQSEventOps._

  // Initialize anything we want to be shared across lambda invocations here

  private val config = ConfigFactory.load("application")

  private object SNSDownstream extends Downstream {
    private val msgSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from batcher")
    override def notify(batch: Batch): Try[Unit] = msgSender.sendT(batch)
  }

  val downstream = config.getString("batcher.use_downstream") match {
    case "sns"   => SNSDownstream
    case "stdio" => STDIODownstream
  }

  // This is the entry point for the lambda

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): String = {
    debug(s"Running batcher lambda, got event: $event")

    val f = PathsProcessor(
      config.requireInt("batcher.max_batch_size"),
      event.extractPaths.map(PathFromString),
      downstream
    )

    // Wait here so that lambda can finish executing correctly.
    // 15 minutes is the maximum time allowed for a lambda to run.
    Await.result(f, 15.minutes)

    "Done"
  }
}
