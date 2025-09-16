package weco.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax._
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** Base class for AWS Lambda functions that are invoked by Step Functions.
  *
  * This abstraction handles:
  *   - JSON parsing of input payload to typed input
  *   - JSON serialization of typed output to response
  *   - Error handling and logging
  *   - Execution timeout management
  *
  * @tparam InputType
  *   The type of the input payload
  * @tparam OutputType
  *   The type of the output response
  * @tparam Config
  *   The application configuration type
  */
abstract class StepFunctionLambdaApp[
  InputType,
  OutputType,
  Config <: ApplicationConfig
]()(
  implicit val inputDecoder: Decoder[InputType],
  implicit val outputEncoder: Encoder[OutputType],
  val ct: ClassTag[InputType]
) extends RequestHandler[String, String]
    with LambdaConfigurable[Config]
    with Logging {

  // 15 minutes is the maximum time allowed for a lambda to run, as of 2024-12-19
  protected val maximumExecutionTime: FiniteDuration = 15.minutes

  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  /** Process the parsed input and return the output.
    *
    * This is the main method that implementations should override to provide
    * their business logic.
    *
    * @param input
    *   The parsed input payload
    * @return
    *   A Future containing the output response
    */
  def processRequest(input: InputType): Future[OutputType]

  /** AWS Lambda entry point handling raw JSON input/output.
    *
    * Lifecycle:
    *   1. Decode raw JSON string into InputType using an implicit Decoder
    *   2. Invoke processRequest with parsed input
    *   3. Await result (bounded by maximumExecutionTime)
    *   4. Encode OutputType to JSON string using an implicit Encoder
    *   5. Return JSON string or fail with an exception (causing Step Function task failure)
    */
  override def handleRequest(
    input: String,
    context: Context
  ): String = {
    info(
      s"Processing Step Function request; expected input type: ${ct.runtimeClass.getSimpleName}"
    )
    debug(
      s"Lambda context - request ID: ${context.getAwsRequestId}, remaining time: ${context.getRemainingTimeInMillis}ms"
    )

    // Step 1: Decode JSON
    val parsedInput: InputType = decode[InputType](input) match {
      case Left(err) =>
        error(s"Failed to decode input JSON: ${err.getMessage}")
        throw new RuntimeException(s"Failed to decode input: ${err.getMessage}", err)
      case Right(value) => value
    }

    // Step 2 & 3: Process with timeout & error handling
    val output: OutputType = Try {
      Await.result(
        processRequest(parsedInput).recover { case ex: Exception =>
          error(s"Error processing Step Function request: ${ex.getMessage}", ex)
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

    // Step 4: Encode output
    Try(output.asJson.noSpaces) match {
      case Success(json) => json
      case Failure(err) =>
        error(s"Failed to encode output: ${err.getMessage}", err)
        throw new RuntimeException(s"Failed to encode output: ${err.getMessage}", err)
    }
  }
}
