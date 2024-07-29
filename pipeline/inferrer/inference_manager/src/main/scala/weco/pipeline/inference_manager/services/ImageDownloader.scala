package weco.pipeline.inference_manager.services

import java.nio.file.{Files, Path, Paths}
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes,
  Uri
}
import org.apache.pekko.stream.scaladsl.FlowWithContext
import org.apache.pekko.stream.{IOResult, Materializer}
import org.apache.pekko.stream.scaladsl.{FileIO, Flow, Keep, Sink}
import org.apache.pekko.util.ByteString
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}
import weco.pipeline.inference_manager.models
import weco.pipeline.inference_manager.models.DownloadedImage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait FileWriter {
  def write(path: Path): Sink[ByteString, Future[IOResult]]
  def delete: Sink[Path, Future[Done]]
}

class ImageDownloader[Ctx](
  requestPool: RequestPoolFlow[(Uri, MergedIdentifiedImage), Ctx],
  fileWriter: FileWriter,
  root: String = "/"
)(implicit materializer: Materializer) {

  implicit val ec: ExecutionContext = materializer.executionContext

  private val parallelism = 10

  def download: FlowWithContext[
    MergedIdentifiedImage,
    Ctx,
    DownloadedImage,
    Ctx,
    NotUsed
  ] =
    FlowWithContext[MergedIdentifiedImage, Ctx]
      .map(createImageFileRequest)
      .via(requestPool.asContextFlow)
      .mapAsync(parallelism)(saveImageFile)
      .map {
        case (image, path) =>
          models.DownloadedImage(image, path)
      }

  def delete: Sink[DownloadedImage, Future[Done]] =
    Flow[DownloadedImage].map(_.path).toMat(fileWriter.delete)(Keep.right)

  def getLocalImagePath(image: MergedIdentifiedImage): Path =
    Paths.get(root, image.id, "default.jpg").toAbsolutePath

  private def createImageFileRequest(
    image: MergedIdentifiedImage
  ): (HttpRequest, (Uri, MergedIdentifiedImage)) =
    getImageUri(image.locations) match {
      case Some(uri) =>
        (HttpRequest(method = HttpMethods.GET, uri = uri), (uri, image))
      case None =>
        throw new RuntimeException(
          s"Could not extract an image URL from locations on image ${image.state.sourceIdentifier}"
        )
    }

  private def saveImageFile: PartialFunction[
    (Try[HttpResponse], (Uri, MergedIdentifiedImage)),
    Future[(MergedIdentifiedImage, Path)]
  ] = {
    case (
          Success(response @ HttpResponse(StatusCodes.OK, _, _, _)),
          (_, image)
        ) =>
      val path = getLocalImagePath(image)
      response.entity.dataBytes
        .runWith(fileWriter.write(path))
        .map {
          _ =>
            (image, path)
        }
    case (Success(failedResponse), (uri, image)) =>
      failedResponse.discardEntityBytes()
      Future.failed(
        throw new RuntimeException(
          s"Image request for $uri failed with status ${failedResponse.status}"
        )
      )
    case (Failure(exception), _) => Future.failed(exception)
  }

  private def getImageUri(locations: List[DigitalLocation]): Option[Uri] =
    locations
      .find(_.locationType == LocationType.IIIFImageAPI)
      .map {
        location =>
          Uri(location.url) match {
            case uri @ Uri(_, _, path, _, _)
                if path.endsWith("info.json", ignoreTrailingSlash = true) =>
              uri.withPath(
                Uri.Path(
                  path
                    .toString()
                    .replace(
                      "info.json",
                      // DLCS provides a thumbnails service which only serves certain sizes of image.
                      // Requests for these don't touch the image server and so, as we're performing
                      // lots of requests, we use 400x400 thumbnails and resize them ourselves later on.
                      "full/!400,400/0/default.jpg"
                    )
                )
              )
            case other => other
          }
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
      requestPool = Http().superPool[((Uri, MergedIdentifiedImage), Message)]()
    )
}
