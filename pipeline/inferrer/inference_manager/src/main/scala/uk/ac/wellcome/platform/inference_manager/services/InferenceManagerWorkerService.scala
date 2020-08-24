package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import akka.http.scaladsl.model.{HttpResponse, Uri}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, FlowWithContext, Source}
import grizzled.slf4j.Logging
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.models.work.internal.{AugmentedImage, InferredData}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.adapters.{
  InferrerAdapter,
  InferrerResponse
}
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InferenceManagerWorkerService[Destination](
  msgStream: BigMessageStream[MergedIdentifiedImage],
  messageSender: MessageSender[Destination],
  imageDownloader: ImageDownloader[Message],
  inferrerAdapters: Set[InferrerAdapter],
  requestPool: RequestPoolFlow[(DownloadedImage, InferrerAdapter), Message]
)(implicit actorSystem: ActorSystem, ec: ExecutionContext)
    extends Runnable
    with Logging {

  val className: String = this.getClass.getSimpleName
  val parallelism = 10

  lazy val adapters: Map[Uri.Authority, InferrerAdapter] =
    inferrerAdapters.map(adapter => adapter.hostAuthority -> adapter).toMap

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      _.asSourceWithContext {
        case (message, _) => message
      }.map {
          case (_, image) => image
        }
        .via(imageDownloader.download)
        .via(createRequests)
        .via(requestPool.asContextFlow)
        .via(unmarshalResponse)
        .via(collectAndAugment)
        .via(sendAugmented)
        .asSource
        .map { case (_, msg) => msg }
    )

  private def createRequests[Ctx] =
    FlowWithContext[DownloadedImage, Ctx].mapConcat { image =>
      inferrerAdapters.map { inferrerAdapter =>
        (
          inferrerAdapter.createRequest(image),
          (image, inferrerAdapter)
        )
      }
    }

  private def unmarshalResponse[Ctx] =
    FlowWithContext[
      (Try[HttpResponse], (DownloadedImage, InferrerAdapter)),
      Ctx]
      .map {
        case result @ (_, (image, _)) =>
          imageDownloader.delete.runWith(Source.single(image))
          result
      }
      .mapAsync(parallelism) {
        case (Success(response), (image, adapter)) =>
          adapter
            .parseResponse(response)
            .recover {
              case e: Exception =>
                response.discardEntityBytes()
                throw e
            }
            .map((image, adapter, _))
        case (Failure(exception), _) =>
          Future.failed(exception)
      }

  private def collectAndAugment[Ctx] =
    FlowWithContext[(DownloadedImage, InferrerAdapter, InferrerResponse), Ctx]
      .via {
        Flow[((DownloadedImage, InferrerAdapter, InferrerResponse), Ctx)]
          .groupBy(inferrerAdapters.size, _ match {
            case ((downloadedImage, _, _), _) =>
              downloadedImage.image.id.canonicalId
          })
          .map {
            case ((downloadedImage, adapter, response), ctx) =>
              val augmentedImage =
                downloadedImage.image.augment(Some(InferredData.empty))
              ((augmentedImage, adapter, response), ctx)
          }
          .reduce {
            case (
                ((combinedAugmentedImage, _, _), ctx),
                ((_, adapter, response), _)) =>
              val inferredData = combinedAugmentedImage.inferredData.map {
                previousInferredData =>
                  response match {
                    case adapterResponse: adapter.Response =>
                      adapter.augment(previousInferredData, adapterResponse)
                  }
              }
              val nextImage =
                combinedAugmentedImage.copy(inferredData = inferredData)
              ((nextImage, adapter, response), ctx)
          }
          .map {
            case ((image, _, _), ctx) => (image, ctx)
          }
          .mergeSubstreams
      }

  private def sendAugmented[Ctx] =
    FlowWithContext[AugmentedImage, Ctx]
      .map(messageSender.sendT[AugmentedImage])
      .map(_.get)

}
