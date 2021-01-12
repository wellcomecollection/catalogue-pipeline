package uk.ac.wellcome.platform.inference_manager.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.duration._
import software.amazon.awssdk.services.sqs.model.Message

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.inference_manager.adapters.InferrerAdapter
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.pipeline_storage.{
  MemoryIndexer,
  MemoryRetriever,
  PipelineStorageConfig
}
import uk.ac.wellcome.platform.inference_manager.services.{
  FileWriter,
  ImageDownloader,
  InferenceManagerWorkerService,
  MergedIdentifiedImage,
  RequestPoolFlow
}
import ImageState.Initial

trait InferenceManagerWorkerServiceFixture extends SQS with Akka {

  def withWorkerService[R](
    queue: Queue,
    msgSender: MemoryMessageSender,
    adapters: Set[InferrerAdapter],
    fileWriter: FileWriter,
    inferrerRequestPool: RequestPoolFlow[(DownloadedImage, InferrerAdapter),
                                         Message],
    imageRequestPool: RequestPoolFlow[MergedIdentifiedImage, Message],
    fileRoot: String = "/",
    initialImages: List[Image[Initial]] = Nil)(
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
          imageIndexer = new MemoryIndexer(),
          pipelineStorageConfig = PipelineStorageConfig(
            batchSize = 1,
            flushInterval = 1 milliseconds,
            parallelism = 1
          ),
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
