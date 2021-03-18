package uk.ac.wellcome.platform.ingestor.images

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.Suite

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  PipelineStorageStreamFixtures
}
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}

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
