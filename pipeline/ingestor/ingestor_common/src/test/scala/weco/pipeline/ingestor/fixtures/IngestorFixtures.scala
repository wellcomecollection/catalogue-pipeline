package weco.pipeline.ingestor.fixtures

import org.scalatest.Suite
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.Queue
import weco.pipeline.ingestor.common.IngestorWorkerService
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.{Indexable, Indexer, Retriever}

import scala.concurrent.ExecutionContext.Implicits.global

trait IngestorFixtures extends PipelineStorageStreamFixtures {
  this: Suite =>

  def withWorkerService[In, Out, R](
    queue: Queue,
    retriever: Retriever[In],
    indexer: Indexer[Out],
    transform: In => Out
  )(
    testWith: TestWith[IngestorWorkerService[String, In, Out], R]
  )(implicit indexable: Indexable[Out]): R =
    withPipelineStream(
      queue,
      indexer,
      pipelineStorageConfig = pipelineStorageConfig
    ) { msgStream =>
      val workerService = new IngestorWorkerService(
        pipelineStream = msgStream,
        retriever = retriever,
        transform = transform
      )

      workerService.run()

      testWith(workerService)
    }
}
