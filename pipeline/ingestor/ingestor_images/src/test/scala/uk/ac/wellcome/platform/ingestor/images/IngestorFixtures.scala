package uk.ac.wellcome.platform.ingestor.images

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.Suite
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.fixtures.{ElasticIndexerFixtures, PipelineStorageStreamFixtures}
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import ImageState.{Augmented, Indexed}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream

trait IngestorFixtures
    extends ElasticIndexerFixtures
    with PipelineStorageStreamFixtures {
  this: Suite =>

  def withWorkerService[R](queue: Queue,
                           retriever: Retriever[Image[Augmented]],
                           indexer: Indexer[Image[Indexed]])(
    testWith: TestWith[ImageIngestorWorkerService[String], R]): R = {
    withActorSystem { implicit ac =>
    withSQSStream(queue) { msgStream: SQSStream[NotificationMessage] =>

        val workerService = new ImageIngestorWorkerService(
          msgStream,
          indexer,
          pipelineStorageConfig,
          new MemoryMessageSender(),
          imageRetriever = retriever
        )

        workerService.run()

        testWith(workerService)
    }
    }
  }
}
