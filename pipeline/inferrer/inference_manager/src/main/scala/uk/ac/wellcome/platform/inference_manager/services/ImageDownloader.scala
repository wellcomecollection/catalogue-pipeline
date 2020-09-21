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
import akka.stream.scaladsl.FlowWithContext
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink}
import akka.util.ByteString
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait FileWriter {
  def write(path: Path): Sink[ByteString, Future[IOResult]]
  def delete: Sink[Path, Future[Done]]
}

class ImageDownloader[Ctx](
  requestPool: RequestPoolFlow[MergedIdentifiedImage, Ctx],
  fileWriter: FileWriter,
  root: String = "/")(implicit materializer: Materializer) {

  implicit val ec: ExecutionContext = materializer.executionContext

  private val parallelism = 10

  def download: FlowWithContext[MergedIdentifiedImage,
                                Ctx,
                                DownloadedImage,
                                Ctx,
                                NotUsed] =
    FlowWithContext[MergedIdentifiedImage, Ctx]
      .map(createImageFileRequest)
      .via(requestPool.asContextFlow)
      .mapAsync(parallelism)(saveImageFile)
      .map {
        case (image, path) => DownloadedImage(image, path)
      }

  def delete: Sink[DownloadedImage, Future[Done]] =
    Flow[DownloadedImage].map(_.path).toMat(fileWriter.delete)(Keep.right)

  def getLocalImagePath(image: MergedIdentifiedImage): Path =
    Paths.get(root, image.id.canonicalId, "default.jpg").toAbsolutePath

  private def createImageFileRequest(
    image: MergedIdentifiedImage): (HttpRequest, MergedIdentifiedImage) = {
    val uri = getImageUri(image.locationDeprecated.url)
    (HttpRequest(method = HttpMethods.GET, uri = uri), image)
  }

  private def saveImageFile: PartialFunction[
    (Try[HttpResponse], MergedIdentifiedImage),
    Future[(MergedIdentifiedImage, Path)]
  ] = {
    case (Success(response @ HttpResponse(StatusCodes.OK, _, _, _)), image) =>
      val path = getLocalImagePath(image)
      response.entity.dataBytes
        .runWith(fileWriter.write(path))
        .map { _ =>
          (image, path)
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

class DefaultFileWriter(root: String) extends FileWriter {
  def write(path: Path): Sink[ByteString, Future[IOResult]] = {
    path.getParent.toFile.mkdirs()
    FileIO.toPath(path)
  }

  def delete: Sink[Path, Future[Done]] =
    Sink.foreach[Path](deletePath)

  private val rootPath = Paths.get(root)

  // Delete path and directories above it until deletion fails (the directory is not empty)
  // or all directories up to the root have been deleted
  @scala.annotation.tailrec
  private def deletePath(path: Path): Unit =
    if (path.toFile.delete && !Files.isSameFile(path.getParent, rootPath)) {
      deletePath(path.getParent)
    }
}

object ImageDownloader {
  def apply(root: String)(implicit actorSystem: ActorSystem) =
    new ImageDownloader(
      root = root,
      fileWriter = new DefaultFileWriter(root),
      requestPool = Http().superPool[(MergedIdentifiedImage, Message)]()
    )
}
