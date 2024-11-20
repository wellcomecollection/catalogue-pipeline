package weco.pipeline.relation_embedder

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import com.amazonaws.services.lambda.runtime.events.SQSEvent

object LambdaMain extends RequestHandler[SQSEvent, String] with Logging {
  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): String = {
    info(s"running relation_embedder lambda, got event")
    "Done"
  }
}
