package weco.pipeline_storage.fixtures

import weco.pekko.fixtures.Pekko
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.monitoring.Metrics
import weco.monitoring.memory.MemoryMetrics
import weco.pipeline_storage._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PipelineStorageStreamFixtures extends Pekko with SQS {
  val pipelineStorageConfig = PipelineStorageConfig(
    batchSize = 1,
    flushInterval = 1 milliseconds,
    parallelism = 1
  )

  def withPipelineStream[T: Indexable, R](
    queue: Queue,
    indexer: Indexer[T],
    sender: MemoryMessageSender = new MemoryMessageSender(),
    metrics: Metrics[Future] = new MemoryMetrics(),
    pipelineStorageConfig: PipelineStorageConfig = pipelineStorageConfig
  )(
    testWith: TestWith[PipelineStorageStream[NotificationMessage, T, String], R]
  ): R =
    withActorSystem {
      implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue, metrics) {
          messageStream =>
            val pipelineStream =
              new PipelineStorageStream[NotificationMessage, T, String](
                messageStream = messageStream,
                indexer = indexer,
                messageSender = sender
              )(
                config = pipelineStorageConfig
              )

            testWith(pipelineStream)
        }
    }
}
