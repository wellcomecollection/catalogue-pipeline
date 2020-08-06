package uk.ac.wellcome.platform.inference_manager.fixtures

import io.circe.Decoder
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
  FileWriter,
  ImageDownloader,
  InferenceManagerWorkerService,
  InferrerAdapter,
  MergedIdentifiedImage,
  RequestPoolFlow
}

import scala.concurrent.ExecutionContext.Implicits.global

trait InferenceManagerWorkerServiceFixture
    extends BigMessagingFixture
    with Akka {
  def withWorkerService[R](
    queue: Queue,
    messageSender: MemoryMessageSender,
    adapter: InferrerAdapter[DownloadedImage, AugmentedImage],
    fileWriter: FileWriter,
    inferrerRequestPool: RequestPoolFlow[DownloadedImage],
    imageRequestPool: RequestPoolFlow[MergedIdentifiedImage])(
    testWith: TestWith[InferenceManagerWorkerService[String], R])(
    implicit decoder: Decoder[MergedIdentifiedImage]): R =
    withActorSystem { implicit actorSystem =>
      withBigMessageStream[MergedImage[Identified, Minted], R](queue) {
        msgStream =>
          val workerService = new InferenceManagerWorkerService(
            msgStream = msgStream,
            messageSender = messageSender,
            inferrerAdapter = adapter,
            imageDownloader = new ImageDownloader(
              fileWriter = fileWriter,
              requestPool = imageRequestPool),
            requestPool = inferrerRequestPool
          )

          workerService.run()

          testWith(workerService)
      }
    }

}
