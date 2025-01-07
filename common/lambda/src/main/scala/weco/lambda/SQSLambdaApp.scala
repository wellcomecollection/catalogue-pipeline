package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import io.circe.Decoder
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

// Unfortunately we can't have an intermediate abstraction here because of an interaction
// of the AWS SDK with the Scala compiler.
// See: https://stackoverflow.com/questions/54098144/aws-lambda-handler-throws-a-classcastexception-with-scala-generics
abstract class SQSLambdaApp[T, Out, Config <: ApplicationConfig]()(
  implicit val decoder: Decoder[T],
  val ct: ClassTag[T]
) extends RequestHandler[SQSEvent, Out]
    with LambdaConfigurable[Config]
    with Logging {

  // 15 minutes is the maximum time allowed for a lambda to run, as of 2024-12-19
  protected val maximumExecutionTime: FiniteDuration = 15.minutes

  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  import weco.lambda.SQSEventOps._

  def processT(t: List[T]): Future[Out]

  override def handleRequest(
    event: SQSEvent,
    context: Context
  ): Out = Await.result(
    processT(event.extract[T]),
    maximumExecutionTime
  )
}
