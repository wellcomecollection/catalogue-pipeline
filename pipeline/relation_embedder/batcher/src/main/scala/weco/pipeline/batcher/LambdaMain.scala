package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging

import java.util.{Map => JavaMap}

object LambdaMain
    extends RequestHandler[JavaMap[String, String], String]
    with Logging {

  override def handleRequest(
    event: JavaMap[String, String],
    context: Context
  ): String = {
    info(s"running batcher lambda, got event: $event")
    "Done"
  }
}
