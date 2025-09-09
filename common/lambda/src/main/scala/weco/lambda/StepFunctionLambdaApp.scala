package weco.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import io.circe.{Decoder, Encoder}
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** Base class for AWS Lambda functions that are invoked by Step Functions.
  *
  * This abstraction handles:
  * - JSON parsing of input payload to typed input
  * - JSON serialization of typed output to response
  * - Error handling and logging
  * - Execution timeout management
  *
  * @tparam InputType The type of the input payload
  * @tparam OutputType The type of the output response
  * @tparam Config The application configuration type
  */
abstract class StepFunctionLambdaApp[
  InputType,
  OutputType,
  Config <: ApplicationConfig
]()(
  implicit val inputDecoder: Decoder[InputType],
  implicit val outputEncoder: Encoder[OutputType],
  val ct: ClassTag[InputType]
) extends RequestHandler[InputType, OutputType]
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
    * @param input The parsed input payload
    * @return A Future containing the output response
    */
  def processRequest(input: InputType): Future[OutputType]

  /** AWS Lambda entry point.
    *
    * This method handles the Lambda invocation lifecycle:
    * 1. Validates input (already parsed by AWS Lambda runtime)
    * 2. Calls processRequest with the input
    * 3. Waits for the result within the timeout
    * 4. Returns the output or throws an exception
    *
    * @param input The input payload (already parsed from JSON by AWS Lambda runtime)
    * @param context The Lambda context
    * @return The output response
    */
  override def handleRequest(
    input: InputType,
    context: Context
  ): OutputType = {
    info(s"Processing Step Function request with input type: ${ct.runtimeClass.getSimpleName}")
    debug(s"Lambda context - request ID: ${context.getAwsRequestId}, remaining time: ${context.getRemainingTimeInMillis}ms")

    Try {
      Await.result(
        processRequest(input).recover {
          case ex: Exception =>
            error(s"Error processing Step Function request: ${ex.getMessage}", ex)
            throw ex
        },
        maximumExecutionTime
      )
    } match {
      case Success(output) =>
        info("Successfully processed Step Function request")
        output
      case Failure(ex) =>
        error(s"Failed to process Step Function request: ${ex.getMessage}", ex)
        throw ex
    }
  }
}