package uk.ac.wellcome.platform.inference_manager.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import software.amazon.awssdk.services.sqs.model.Message

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.inference_manager.adapters.InferrerAdapter
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.pipeline_storage.{MemoryIndexer, MemoryRetriever}
import uk.ac.wellcome.platform.inference_manager.services.{
  FileWriter,
  ImageDownloader,
  InferenceManagerWorkerService,
  MergedIdentifiedImage,
  RequestPoolFlow
}
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.catalogue.internal_model.image.ImageState.{Augmented, Initial}
import akka.http.scaladsl.model.Uri
import weco.catalogue.internal_model.image.Image

trait InferenceManagerWorkerServiceFixture
    extends PipelineStorageStreamFixtures {

  def withWorkerService[R](
    queue: Queue,
    msgSender: MemoryMessageSender,
    adapters: Set[InferrerAdapter],
    fileWriter: FileWriter,
    inferrerRequestPool: RequestPoolFlow[(DownloadedImage, InferrerAdapter),
                                         Message],
    imageRequestPool: RequestPoolFlow[(Uri, MergedIdentifiedImage), Message],
    fileRoot: String = "/",
    initialImages: List[Image[Initial]] = Nil,
    augmentedImages: mutable.Map[String, Image[Augmented]])(
    testWith: TestWith[InferenceManagerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { msgStream =>
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
            requestPool = imageRequestPool),
          requestPool = inferrerRequestPool
        )

        workerService.run()

        testWith(workerService)
      }
    }

}
