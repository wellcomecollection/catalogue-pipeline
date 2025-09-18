package weco.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.apache.pekko.actor.ActorSystem

import weco.lambda.JavaMapJsonCodec._
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** Base class for AWS Lambda functions that are invoked by Step Functions.
  *
  * AWS Java runtime will (when no specific POJO is provided) deserialise JSON
  * objects to LinkedHashMap[String, AnyRef]. We adapt that generic structure
  * into a typed InputType using Circe, and encode the typed OutputType back
  * into a LinkedHashMap for the runtime to serialise as JSON.
  */
abstract class StepFunctionLambdaApp[
  InputType,
  OutputType,
  Config <: ApplicationConfig
]()(
  implicit val inputDecoder: Decoder[InputType],
  implicit val outputEncoder: Encoder[OutputType],
  val ct: ClassTag[InputType]
) extends RequestHandler[
      java.util.LinkedHashMap[String, AnyRef],
      java.util.LinkedHashMap[String, AnyRef]
    ]
    with LambdaConfigurable[Config]
    with Logging {

  // 15 minutes is the maximum time allowed for a lambda to run, as of 2024-12-19
  protected val maximumExecutionTime: FiniteDuration = 15.minutes

  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  /** Implementations supply business logic here. */
  def processRequest(input: InputType): Future[OutputType]

  /** AWS Lambda entry point handling LinkedHashMap-based (already parsed) JSON.
    */
  override def handleRequest(
    rawInput: java.util.LinkedHashMap[String, AnyRef],
    context: Context
  ): java.util.LinkedHashMap[String, AnyRef] = {
    info(
      s"Processing Step Function request (Java Map); expected input type: ${ct.runtimeClass.getSimpleName}; fieldCount=${rawInput.size()}"
    )
    debug(
      s"Lambda context - request ID: ${context.getAwsRequestId}, remaining time: ${context.getRemainingTimeInMillis}ms"
    )

    // Decode
    val inputJson = javaMapToJson(rawInput)
    val parsedInput: InputType = inputJson.as[InputType] match {
      case Left(err) =>
        error(s"Failed to decode input JSON: ${err.getMessage}")
        throw new RuntimeException(
          s"Failed to decode input: ${err.getMessage}",
          err
        )
      case Right(value) => value
    }

    // Process with timeout + recovery logging
    val output: OutputType = Try {
      Await.result(
        processRequest(parsedInput).recover {
          case ex: Exception =>
            error(
              s"Error processing Step Function request: ${ex.getMessage}",
              ex
            )
            throw ex
        },
        maximumExecutionTime
      )
    } match {
      case Success(o) =>
        info("Successfully processed Step Function request")
        o
      case Failure(ex) =>
        error(s"Failed to process Step Function request: ${ex.getMessage}", ex)
        throw ex
    }

    // Encode
    val outputJson = output.asJson
    outputJson.asObject match {
      case Some(obj) => jsonObjectToMap(obj)
      case None =>
        error(
          "Output JSON was not an object; cannot serialise to LinkedHashMap"
        )
        throw new RuntimeException("Output JSON must be a JSON object")
    }
  }
}
