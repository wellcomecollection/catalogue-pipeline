package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.Suite

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Indexed}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  PipelineStorageStreamFixtures
}
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}

trait IngestorFixtures
    extends ElasticIndexerFixtures
    with PipelineStorageStreamFixtures {
  this: Suite =>

  def withWorkerService[R](queue: Queue,
                           retriever: Retriever[Work[Denormalised]],
                           indexer: Indexer[Work[Indexed]])(
    testWith: TestWith[WorkIngestorWorkerService[String], R]): R = {
    withPipelineStream(queue, indexer, pipelineStorageConfig = pipelineStorageConfig) {
      msgStream =>
        val workerService = new WorkIngestorWorkerService(
          pipelineStream = msgStream,
          workRetriever = retriever
        )

        workerService.run()

        testWith(workerService)
    }
  }
}
