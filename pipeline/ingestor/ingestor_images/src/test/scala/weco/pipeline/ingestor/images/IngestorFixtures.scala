package weco.pipeline.ingestor.images

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.Suite
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.Queue
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}
import weco.pipeline_storage.{Indexer, Retriever}
import weco.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  PipelineStorageStreamFixtures
}

trait IngestorFixtures
    extends ElasticIndexerFixtures
    with PipelineStorageStreamFixtures {
  this: Suite =>

  def withWorkerService[R](queue: Queue,
                           retriever: Retriever[Image[Augmented]],
                           indexer: Indexer[Image[Indexed]])(
    testWith: TestWith[ImageIngestorWorkerService[String], R]): R = {
    withPipelineStream(
      queue,
      indexer,
      pipelineStorageConfig = pipelineStorageConfig) { msgStream =>
      val workerService = new ImageIngestorWorkerService(
        pipelineStream = msgStream,
        imageRetriever = retriever
      )

      workerService.run()

      testWith(workerService)
    }
  }
}
