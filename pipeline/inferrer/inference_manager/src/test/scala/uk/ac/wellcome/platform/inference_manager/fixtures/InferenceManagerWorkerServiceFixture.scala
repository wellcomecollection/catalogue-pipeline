package uk.ac.wellcome.platform.inference_manager.fixtures

import scala.concurrent.ExecutionContext.Implicits.global

import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.inference_manager.adapters.InferrerAdapter
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.platform.inference_manager.services.{
  FileWriter,
  ImageDownloader,
  InferenceManagerWorkerService,
  MergedIdentifiedImage,
  RequestPoolFlow
}

trait InferenceManagerWorkerServiceFixture
    extends BigMessagingFixture
    with Akka {
  def withWorkerService[R](
    queue: Queue,
    messageSender: MemoryMessageSender,
    adapters: Set[InferrerAdapter],
    fileWriter: FileWriter,
    inferrerRequestPool: RequestPoolFlow[(DownloadedImage, InferrerAdapter),
                                         Message],
    imageRequestPool: RequestPoolFlow[MergedIdentifiedImage, Message],
    fileRoot: String = "/")(
    testWith: TestWith[InferenceManagerWorkerService[String], R])(
    implicit decoder: Decoder[MergedIdentifiedImage]): R =
    withActorSystem { implicit actorSystem =>
      withBigMessageStream[Image[ImageState.Initial], R](queue) {
        msgStream =>
          val workerService = new InferenceManagerWorkerService(
            msgStream = msgStream,
            messageSender = messageSender,
            inferrerAdapters = adapters,
            imageDownloader = new ImageDownloader(
              root = fileRoot,
              fileWriter = fileWriter,
              requestPool = imageRequestPool),
            requestPool = inferrerRequestPool
          )

          workerService.run()

          testWith(workerService)
      }
    }

}
