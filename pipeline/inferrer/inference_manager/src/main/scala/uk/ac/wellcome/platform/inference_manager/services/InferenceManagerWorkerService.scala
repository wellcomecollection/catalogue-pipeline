package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import akka.http.scaladsl.model.HttpResponse
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FlowWithContext, Source}
import grizzled.slf4j.Logging
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InferenceManagerWorkerService[Destination](
  msgStream: BigMessageStream[MergedIdentifiedImage],
  messageSender: MessageSender[Destination],
  imageDownloader: ImageDownloader[Message],
  inferrerAdapter: InferrerAdapter[DownloadedImage, AugmentedImage],
  requestPool: RequestPoolFlow[DownloadedImage, Message]
)(implicit actorSystem: ActorSystem, ec: ExecutionContext)
    extends Runnable
    with Logging {

  val className: String = this.getClass.getSimpleName
  val parallelism = 10

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      _.asSourceWithContext {
        case (message, _) => message
      }.map {
          case (_, image) => image
        }
        .via(imageDownloader.download)
        .via(createRequest)
        .via(requestPool.asContextFlow)
        .via(unmarshalResponse)
        .via(augmentInput)
        .via(sendAugmented)
        .asSource
        .map { case (_, msg) => msg }
    )

  private def createRequest[Ctx] =
    FlowWithContext[DownloadedImage, Ctx].map { image =>
      (inferrerAdapter.createRequest(image), image)
    }

  private def unmarshalResponse[Ctx] =
    FlowWithContext[(Try[HttpResponse], DownloadedImage), Ctx]
      .map {
        case result @ (_, image) =>
          imageDownloader.delete.runWith(Source.single(image))
          result
      }
      .mapAsync(parallelism) {
        case (Success(response), image) =>
          inferrerAdapter
            .parseResponse(response)
            .recover {
              case e: Exception =>
                response.discardEntityBytes()
                throw e
            }
            .map((image, _))
        case (Failure(exception), _) =>
          Future.failed(exception)
      }

  private def augmentInput[Ctx] =
    FlowWithContext[
      (DownloadedImage, Option[inferrerAdapter.InferrerResponse]),
      Ctx]
      .map {
        case (image, response) =>
          inferrerAdapter.augmentInput(image, response)
      }

  private def sendAugmented[Ctx] =
    FlowWithContext[AugmentedImage, Ctx]
      .map(messageSender.sendT[AugmentedImage])
      .map(_.get)

}
