package uk.ac.wellcome.platform.inference_manager.fixtures

import java.nio.file.Path

import akka.http.scaladsl.Http
import akka.stream.IOResult
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  Identified,
  MergedImage,
  Minted
}
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.platform.inference_manager.services.{
  ImageDownloader,
  InferenceManagerWorkerService,
  InferrerAdapter
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait InferenceManagerWorkerServiceFixture
    extends BigMessagingFixture
    with Akka {
  def withWorkerService[R](
    queue: Queue,
    messageSender: MemoryMessageSender,
    adapter: InferrerAdapter[DownloadedImage, AugmentedImage],
    inferrerPort: Int)(
    testWith: TestWith[InferenceManagerWorkerService[String], R])(
    implicit decoder: Decoder[MergedImage[Identified, Minted]]): R =
    withActorSystem { implicit actorSystem =>
      withBigMessageStream[MergedImage[Identified, Minted], R](queue) {
        msgStream =>
          val workerService = new InferenceManagerWorkerService(
            msgStream = msgStream,
            messageSender = messageSender,
            inferrerAdapter = adapter,
            imageDownloader = new ImageDownloader(fileWriter = mockFileWriter),
            inferrerClientFlow = Http()
              .cachedHostConnectionPool[(Message, DownloadedImage)](
                "localhost",
                inferrerPort)
          )

          workerService.run()

          testWith(workerService)
      }
    }

  private def mockFileWriter: Sink[(ByteString, Path), Future[IOResult]] =
    Sink.ignore.mapMaterializedValue(_.map(_ => IOResult(1)))
}
