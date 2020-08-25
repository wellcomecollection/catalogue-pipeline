package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import akka.http.scaladsl.model.HttpResponse
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
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class AdapterResponseBundle[ImageType](
  image: ImageType,
  adapter: InferrerAdapter,
  response: InferrerResponse
)

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
  val maxInferrerWait = 30 seconds

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
            .map { response =>
              AdapterResponseBundle(
                image,
                adapter,
                response
              )
            }
        case (Failure(exception), _) =>
          Future.failed(exception)
      }

  private def collectAndAugment[Ctx] =
    FlowWithContext[AdapterResponseBundle[DownloadedImage], Ctx]
      .via {
        Flow[(AdapterResponseBundle[DownloadedImage], Ctx)]
          .groupBy(parallelism, _ match {
            case (AdapterResponseBundle(downloadedImage, _, _), _) =>
              downloadedImage.image.id.canonicalId
          })
          .groupedWithin(inferrerAdapters.size, maxInferrerWait)
          .map { elements =>
            if (elements.size != inferrerAdapters.size) {
              val failedInferrers =
                inferrerAdapters.map(_.getClass.getSimpleName) --
                  elements.map(_._1.adapter.getClass.getSimpleName).toSet
              throw new Exception(
                s"Did not receive responses from all $failedInferrers within $maxInferrerWait")
            }
            elements
          }
          .map { elements =>
            val inferredData = elements.foldLeft(InferredData.empty) {
              case (data, (AdapterResponseBundle(_, adapter, response), _)) =>
                adapter.augment(data, response.asInstanceOf[adapter.Response])
            }
            elements.head match {
              case (
                  AdapterResponseBundle(DownloadedImage(image, _), _, _),
                  ctx) =>
                (image.augment(Some(inferredData)), ctx)
            }
          }
          .mergeSubstreams
      }

  private def sendAugmented[Ctx] =
    FlowWithContext[AugmentedImage, Ctx]
      .map(messageSender.sendT[AugmentedImage])
      .map(_.get)

}
