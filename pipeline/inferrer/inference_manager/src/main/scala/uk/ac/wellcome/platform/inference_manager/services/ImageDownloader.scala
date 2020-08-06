package uk.ac.wellcome.platform.inference_manager.services

import java.nio.file.{Files, Path, Paths}

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes,
  Uri
}
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.util.ByteString
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait FileWriter {
  def write(implicit materializer: Materializer)
    : Sink[(ByteString, Path), Future[IOResult]]
  def delete(implicit materializer: Materializer): Sink[Path, Future[Done]]
}

class ImageDownloader(root: String = "/",
                      requestPool: RequestPoolFlow[MergedIdentifiedImage],
                      fileWriter: FileWriter)(implicit actorSystem: ActorSystem,
                                              ec: ExecutionContext) {

  private val parallelism = 10

  def download: Flow[MessagePair[MergedIdentifiedImage],
                     MessagePair[DownloadedImage],
                     NotUsed] =
    Flow[MessagePair[MergedIdentifiedImage]]
      .map(createImageFileRequest)
      .via(requestPool)
      .mapAsyncUnordered(parallelism)(saveImageFile)
      .map {
        case (message, image, path) =>
          message -> DownloadedImage(image, path)
      }

  def delete: Sink[DownloadedImage, Future[Done]] =
    Flow[DownloadedImage].map(_.path).toMat(fileWriter.delete)(Keep.right)

  def getLocalImagePath(image: MergedIdentifiedImage): Path =
    Paths.get(root, image.id.canonicalId, "default.jpg").toAbsolutePath

  private def createImageFileRequest
    : PartialFunction[MessagePair[MergedIdentifiedImage],
                      (HttpRequest, MessagePair[MergedIdentifiedImage])] = {
    case (msg, image) =>
      val uri = getImageUri(image.location.url)
      (HttpRequest(method = HttpMethods.GET, uri = uri), (msg, image))
  }

  private def saveImageFile: PartialFunction[
    (Try[HttpResponse], MessagePair[MergedIdentifiedImage]),
    Future[(Message, MergedIdentifiedImage, Path)]
  ] = {
    case (
        Success(response @ HttpResponse(StatusCodes.OK, _, _, _)),
        (msg, image)) =>
      val path = getLocalImagePath(image)
      response.entity.dataBytes
        .map(file => file -> path)
        .runWith(fileWriter.write)
        .map { _ =>
          (msg, image, path)
        }
    case (Success(failedResponse), _) =>
      failedResponse.discardEntityBytes()
      Future.failed(
        throw new RuntimeException(
          s"Image request failed with status ${failedResponse.status}"))
    case (Failure(exception), _) => Future.failed(exception)
  }

  private def getImageUri(locationUrl: String): Uri =
    Uri(locationUrl) match {
      case uri @ Uri(_, _, path, _, _)
          if path.endsWith("info.json", ignoreTrailingSlash = true) =>
        uri.withPath(
          Uri.Path(
            path
              .toString()
              .replace(
                "info.json",
                if (uri.authority.host.address() contains "dlcs") {
                  // DLCS provides a thumbnails service which only serves certain sizes of image.
                  // Requests for these don't touch the image server and so, as we're performing
                  // lots of requests, we use 400x400 thumbnails and resize them ourselves later on.
                  "full/!400,400/0/default.jpg"
                } else {
                  "full/224,224/0/default.jpg"
                }
              )
          )
        )
      case other => other
    }
}

object DefaultFileWriter extends FileWriter {
  def write(implicit materializer: Materializer)
    : Sink[(ByteString, Path), Future[IOResult]] =
    Flow[(ByteString, Path)]
      .map {
        case (file, path) =>
          Files.createDirectories(path)
          Source
            .single(file)
            .runWith(FileIO.toPath(path))
      }
      .toMat(Sink.head)(Keep.right)
      .mapMaterializedValue(_.flatten)

  def delete(implicit materializer: Materializer): Sink[Path, Future[Done]] =
    Sink.foreach[Path](deletePath)

  // Delete path and directories above it until deletion fails (the directory is not empty)
  @scala.annotation.tailrec
  private def deletePath(path: Path): Unit =
    if (path.toFile.delete) {
      deletePath(path.getParent)
    }
}

object ImageDownloader {
  def apply(root: String)(implicit actorSystem: ActorSystem,
                          ec: ExecutionContext) =
    new ImageDownloader(
      root,
      fileWriter = DefaultFileWriter,
      requestPool = Http().superPool[MessagePair[MergedIdentifiedImage]]()
    )
}
