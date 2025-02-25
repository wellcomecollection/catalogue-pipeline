package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import io.circe.Decoder
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse
import scala.collection.JavaConverters._

// Unfortunately we can't have an intermediate abstraction here because of an interaction
// of the AWS SDK with the Scala compiler.
// See: https://stackoverflow.com/questions/54098144/aws-lambda-handler-throws-a-classcastexception-with-scala-generics
abstract class SQSBatchResponseLambdaApp[
  T,
  Config <: ApplicationConfig
]()(
  implicit val decoder: Decoder[T],
  val ct: ClassTag[T]
) extends RequestHandler[SQSEvent, SQSBatchResponse]
    with LambdaConfigurable[Config]
    with Logging {

  // 15 minutes is the maximum time allowed for a lambda to run, as of 2024-12-19
  protected val maximumExecutionTime: FiniteDuration = 15.minutes

  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  import weco.lambda.SQSEventOps._

  def processMessages(
    messages: Seq[SQSLambdaMessage[T]]
  ): Future[Seq[SQSLambdaMessageResult]]

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): SQSBatchResponse = {
    val extractedEvents = event.extractLambdaEvents[T]

    // Extracted event failures are presumed permanent so we do not include them in batch failures
    extractedEvents.collect {
      case Left(failure) =>
        error(
          s"Failed to extract message ${failure.messageId} with error ${failure.error.getMessage}"
        )
        debug(
          s"Failed message body for ${failure.messageId}: ${failure.messageBody}"
        )
    }

    Await.result(
      processMessages(extractedEvents.collect {
        case Right(message) => message
      }) map {
        results =>
          results.collect {
            case failure: SQSLambdaMessageFailedRetryable =>
              error(
                s"Failed to process message ${failure.messageId} with error ${failure.error.getMessage}"
              )
              Some(failure)
            case permanentFailure: SQSLambdaMessageFailedPermanent =>
              error(
                s"Failed to process message ${permanentFailure.messageId} with error ${permanentFailure.error.getMessage}"
              )
              None
            case success: SQSLambdaMessageProcessed =>
              info(s"Successfully processed message ${success.messageId}")
              None
          } flatten
      } map {
        _.map {
          case failure: SQSLambdaMessageFailedRetryable =>
            new SQSBatchResponse.BatchItemFailure(failure.messageId)
        }.asJava
      } map {
        new SQSBatchResponse(_)
      },
      maximumExecutionTime
    )
  }
}
