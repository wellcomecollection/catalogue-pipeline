package weco.pipeline.inference_manager.fixtures

import akka.http.scaladsl.model.Uri
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Initial}
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline.inference_manager.adapters.InferrerAdapter
import weco.pipeline.inference_manager.models.DownloadedImage
import weco.pipeline.inference_manager.services.{
  FileWriter,
  ImageDownloader,
  InferenceManagerWorkerService,
  MergedIdentifiedImage,
  RequestPoolFlow
}
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

trait InferenceManagerWorkerServiceFixture
    extends PipelineStorageStreamFixtures {

  def withWorkerService[R](
    queue: Queue,
    msgSender: MemoryMessageSender,
    adapters: Set[InferrerAdapter],
    fileWriter: FileWriter,
    inferrerRequestPool: RequestPoolFlow[
      (DownloadedImage, InferrerAdapter),
      Message
    ],
    imageRequestPool: RequestPoolFlow[(Uri, MergedIdentifiedImage), Message],
    fileRoot: String = "/",
    initialImages: List[Image[Initial]] = Nil,
    augmentedImages: mutable.Map[String, Image[Augmented]]
  )(testWith: TestWith[InferenceManagerWorkerService[String], R]): R =
    withActorSystem {
      implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) {
          msgStream =>
            val workerService = new InferenceManagerWorkerService(
              msgStream = msgStream,
              msgSender = msgSender,
              imageRetriever = new MemoryRetriever[Image[Initial]](
                index = mutable.Map(
                  initialImages.map(image => image.id -> image): _*
                )
              ),
              imageIndexer = new MemoryIndexer(augmentedImages),
              pipelineStorageConfig = pipelineStorageConfig,
              inferrerAdapters = adapters,
              imageDownloader = new ImageDownloader(
                root = fileRoot,
                fileWriter = fileWriter,
                requestPool = imageRequestPool
              ),
              requestPool = inferrerRequestPool
            )

            workerService.run()

            testWith(workerService)
        }
    }

}
