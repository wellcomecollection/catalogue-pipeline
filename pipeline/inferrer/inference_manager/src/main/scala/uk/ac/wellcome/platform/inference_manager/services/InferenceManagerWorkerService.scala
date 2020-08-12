package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import grizzled.slf4j.Logging
import io.circe.Encoder
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InferenceManagerWorkerService[Destination, Input, Output](
  msgStream: BigMessageStream[Input],
  messageSender: MessageSender[Destination],
  inferrerAdapter: InferrerAdapter[Input, Output],
  inferrerClientFlow: Flow[(HttpRequest, (Message, Input)),
                           (Try[HttpResponse], (Message, Input)),
                           HostConnectionPool]
)(implicit actorSystem: ActorSystem,
  ec: ExecutionContext,
  encoder: Encoder[Output])
    extends Runnable
    with Logging {

  val className: String = this.getClass.getSimpleName
  val parallelism = 10

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      _.via(createRequest)
        .via(inferrerClientFlow)
        .via(unmarshalResponse)
        .via(augmentInput)
        .via(sendAugmented)
        .map { case (msg, _) => msg }
    )

  private def createRequest =
    Flow[(Message, Input)].map {
      case (msg, input) =>
        (inferrerAdapter.createRequest(input), (msg, input))
    }

  private def unmarshalResponse =
    Flow[(Try[HttpResponse], (Message, Input))]
      .mapAsync(parallelism) {
        case (Success(response), (msg, input)) =>
          inferrerAdapter
            .parseResponse(response)
            .recover {
              case e: Exception =>
                response.discardEntityBytes()
                throw e
            }
            .map((msg, input, _))
        case (Failure(exception), _) =>
          Future.failed(exception)
      }

  private def augmentInput =
    Flow[(Message, Input, Option[inferrerAdapter.InferrerResponse])].map {
      case (msg, input, response) =>
        (msg, inferrerAdapter.augmentInput(input, response))
    }

  private def sendAugmented =
    Flow[(Message, Output)].map {
      case (msg, image) =>
        messageSender
          .sendT(image)
          .map((msg, _))
          .get
    }

}
