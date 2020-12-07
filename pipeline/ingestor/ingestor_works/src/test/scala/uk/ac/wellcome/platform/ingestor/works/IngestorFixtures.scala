package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.Suite

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures
import uk.ac.wellcome.models.work.internal._
import WorkState.{Identified, Indexed}

trait IngestorFixtures
    extends ElasticIndexerFixtures
    with SQS
    with Akka {
  this: Suite =>

  def withWorkerService[R](queue: Queue,
                           retriever: Retriever[Work[Identified]],
                           indexer: Indexer[Work[Indexed]])(
    testWith: TestWith[WorkIngestorWorkerService, R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](
        queue,
        new MemoryMetrics) { msgStream =>
          val ingestorConfig = IngestorConfig(
            batchSize = 100,
            flushInterval = 1 seconds
          )

          val workerService = new WorkIngestorWorkerService(
            workIndexer = indexer,
            workRetriever = retriever,
            ingestorConfig = ingestorConfig,
            msgStream = msgStream,
          )

          workerService.run()

          testWith(workerService)
      }
    }
}
