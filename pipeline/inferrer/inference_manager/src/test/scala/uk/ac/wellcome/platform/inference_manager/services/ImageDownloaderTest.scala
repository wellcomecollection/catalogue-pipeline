package uk.ac.wellcome.platform.inference_manager.services

import java.nio.file.{Path, Paths}

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.platform.inference_manager.fixtures.{
  MemoryFileWriter,
  RequestPoolFixtures,
  RequestPoolMock,
  Responses
}
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage

class ImageDownloaderTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with ImageGenerators
    with RequestPoolFixtures
    with Akka {

  describe("download") {

    it("makes a request for an image") {
      withMaterializer { implicit materializer =>
        withDownloaderAndFileWriter() {
          case (downloader, requestPool, _) =>
            val image = createIdentifiedMergedImageWith(
              location = createDigitalLocationWith(
                url = "http://images.com/this-image.jpg"
              )
            )
            val result = Source
              .single(image)
              .asSourceWithContext(_ => ())
              .via(downloader.download)
              .runWith(Sink.ignore)

            whenReady(result) { _ =>
              requestPool.requests should have size 1
              requestPool.requests.keys.head.uri.toString should be(
                image.location.url)
            }
        }
      }
    }

    it("saves the image and outputs the path") {
      withMaterializer { implicit materializer: Materializer =>
        withDownloaderAndFileWriter() {
          case (downloader, _, fileWriter) =>
            val image = createIdentifiedMergedImageWith()
            val result = Source
              .single(image)
              .asSourceWithContext(_ => ())
              .via(downloader.download)
              .runWith(Sink.last)

            whenReady(result) {
              case (downloadedImage, _) =>
                fileWriter.files should have size 1
                fileWriter.files.keys.head should be(downloadedImage.path)
            }
        }
      }
    }

    it("fails when the image can't be downloaded") {
      withMaterializer { implicit materializer: Materializer =>
        withDownloaderAndFileWriter(_ => None) {
          case (downloader, _, _) =>
            val image = createIdentifiedMergedImageWith()
            val result = Source
              .single(image)
              .asSourceWithContext(_ => ())
              .via(downloader.download)
              .runWith(Sink.ignore)

            result.failed.futureValue should not be null
        }
      }
    }
  }

  describe("delete") {
    it("deletes a DownloadedImage") {
      val image = createIdentifiedMergedImageWith()
      val downloadedImage = DownloadedImage(
        image,
        Paths.get("a", "b", "c", "default.jpg")
      )
      withMaterializer { implicit materializer: Materializer =>
        withDownloaderAndFileWriter(
          existingFilePaths = Set(downloadedImage.path)) {
          case (downloader, _, fileWriter) =>
            val result = Source
              .single(downloadedImage)
              .runWith(downloader.delete)

            whenReady(result) { _ =>
              fileWriter.files shouldBe empty
            }
        }
      }
    }
  }

  def withDownloaderAndFileWriter[R](response: String => Option[HttpResponse] =
                                       _ => Some(Responses.image),
                                     existingFilePaths: Set[Path] = Set.empty)(
    testWith: TestWith[(ImageDownloader[Unit],
                        RequestPoolMock[MergedIdentifiedImage, Unit],
                        MemoryFileWriter),
                       R])(implicit materializer: Materializer): R =
    withRequestPool[MergedIdentifiedImage, Unit, R](response) { requestPool =>
      val fileWriter = new MemoryFileWriter
      existingFilePaths.foreach { existingFile =>
        fileWriter.files
          .put(existingFile, ByteString(Responses.randomImageBytes()))
      }
      val downloader =
        new ImageDownloader(
          requestPool = requestPool.pool,
          fileWriter = fileWriter)
      testWith((downloader, requestPool, fileWriter))
    }
}
