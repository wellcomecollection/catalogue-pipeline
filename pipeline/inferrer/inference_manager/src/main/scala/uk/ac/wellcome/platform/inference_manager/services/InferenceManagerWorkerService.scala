package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  InferredData,
  MergedImage
}
import uk.ac.wellcome.platform.inference_manager.models.InferrerResponse
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class InferenceManagerWorkerService[Destination, Id](
  msgStream: BigMessageStream[MergedImage[Id]],
  msgSender: BigMessageSender[Destination, AugmentedImage[Id]],
  inferrerAdapter: InferrerAdapter[MergedImage[Id], InferrerResponse],
  inferrerClientFlow: Flow[(HttpRequest, (Message, MergedImage[Id])),
                           (Try[HttpResponse], (Message, MergedImage[Id])),
                           HostConnectionPool]
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable {

  val className: String = this.getClass.getSimpleName
  val parallelism = 2

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      _.via(createRequest)
        .via(inferrerClientFlow)
        .via(unmarshalResponse)
        .via(augmentImage)
        .via(sendAugmentedImage)
        .map { case (msg, _) => msg }
    )

  private def createRequest =
    Flow[(Message, MergedImage[Id])].map {
      case (msg, image) =>
        (inferrerAdapter.createRequest(image), (msg, image))
    }

  private def unmarshalResponse =
    Flow[(Try[HttpResponse], (Message, MergedImage[Id]))]
      .mapAsync(parallelism) {
        case (tryResponse, (msg, image)) =>
          tryResponse match {
            case Success(response) =>
              inferrerAdapter
                .parseResponse(response)
                .map((msg, image, _))
            case Failure(exception) =>
              Future.failed(exception)
          }
      }

  private def augmentImage =
    Flow[(Message, MergedImage[Id], InferrerResponse)].map {
      case (msg, image, InferrerResponse(features, lsh_encoded_features)) =>
        val augmentedImage = image.augment {
          val (features1, features2) = features.splitAt(features.size / 2)
          InferredData(
            features1 = features1,
            features2 = features2,
            lshEncodedFeatures = lsh_encoded_features
          )
        }
        (msg, augmentedImage)
    }

  private def sendAugmentedImage =
    Flow[(Message, AugmentedImage[Id])].mapAsync(parallelism) {
      case (msg, image) =>
        Future.fromTry { msgSender.sendT(image) }.map((msg, _))
    }

}
