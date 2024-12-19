package weco.pipeline.relation_embedder.lib

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait LambdaApp[In, Out, Config <: ApplicationConfig]
    extends RequestHandler[In, Out]
    with LambdaConfigurable[Config]
    with Logging {

  // 15 minutes is the maximum time allowed for a lambda to run, as of 2024-12-19
  protected val maximumExecutionTime: FiniteDuration = 15.minutes

  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  def processEvent(in: In): Future[Out]

  override def handleRequest(
    event: In,
    context: Context
  ): Out = Await.result(
    processEvent(event),
    maximumExecutionTime
  )
}
