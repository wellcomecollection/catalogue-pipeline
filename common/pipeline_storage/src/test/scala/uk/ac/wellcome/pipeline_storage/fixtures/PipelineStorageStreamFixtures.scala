package uk.ac.wellcome.pipeline_storage.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.pipeline_storage._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PipelineStorageStreamFixtures extends Akka with SQS {
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
    pipelineStorageConfig: PipelineStorageConfig = pipelineStorageConfig)(
    testWith: TestWith[PipelineStorageStream[NotificationMessage, T, String],
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { messageStream =>
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
