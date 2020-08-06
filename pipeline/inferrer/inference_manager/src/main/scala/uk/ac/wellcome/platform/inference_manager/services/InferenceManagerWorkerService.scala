package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import akka.http.scaladsl.model.HttpResponse
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Source}
import software.amazon.awssdk.services.sqs.model.Message
import grizzled.slf4j.Logging
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
  imageDownloader: ImageDownloader,
  inferrerAdapter: InferrerAdapter[DownloadedImage, AugmentedImage],
  requestPool: RequestPoolFlow[DownloadedImage]
)(implicit actorSystem: ActorSystem, ec: ExecutionContext)
    extends Runnable
    with Logging {

  val className: String = this.getClass.getSimpleName
  val parallelism = 10

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      _.via(imageDownloader.download)
        .via(createRequest)
        .via(requestPool)
        .via(unmarshalResponse)
        .via(augmentInput)
        .via(sendAugmented)
        .map { case (msg, _) => msg }
    )

  private def createRequest =
    Flow[MessagePair[DownloadedImage]].map {
      case (msg, image) =>
        (inferrerAdapter.createRequest(image), (msg, image))
    }

  private def unmarshalResponse =
    Flow[(Try[HttpResponse], MessagePair[DownloadedImage])]
      .map {
        case result @ (_, (_, image)) =>
          imageDownloader.delete.runWith(Source.single(image))
          result
      }
      .mapAsyncUnordered(parallelism) {
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
    Flow[(Message, DownloadedImage, Option[inferrerAdapter.InferrerResponse])]
      .map {
        case (msg, image, response) =>
          msg -> inferrerAdapter.augmentInput(image, response)
      }

  private def sendAugmented =
    Flow[MessagePair[AugmentedImage]].map {
      case (msg, image) =>
        messageSender
          .sendT(image)
          .map((msg, _))
          .get
    }

}
