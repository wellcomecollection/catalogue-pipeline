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
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage

import scala.collection.JavaConverters._
import scala.util.Failure

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

  def processT(t: List[T]): Future[Seq[T]]

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): SQSBatchResponse = {
    val messagesMap = event.extractLambdaEvents[T]
    val (permanentFailures, toProcess) = messagesMap.partition(_.isFailure)

    permanentFailures.foreach {
      case Failure(e) => error(s"Failed to extract message, not retrying (${e.getMessage})")
    }



    Await.result(
      processToBatchResponse(messagesMap),
      maximumExecutionTime
    )
  }

  private def processToBatchResponse(
    messagesMap: Map[T, Seq[SQSMessage]]
  ): Future[SQSBatchResponse] = {
    processT(messagesMap.keySet.toList) map {
      failures =>
        failures.flatMap {
          failure => messagesMap(failure)
        }
    } map {
      failedMessages: Seq[SQSMessage] =>
        new SQSBatchResponse(
          failedMessages
            .map(msg => new SQSBatchResponse.BatchItemFailure(msg.getMessageId))
            .asJava
        )
    }
  }
}
