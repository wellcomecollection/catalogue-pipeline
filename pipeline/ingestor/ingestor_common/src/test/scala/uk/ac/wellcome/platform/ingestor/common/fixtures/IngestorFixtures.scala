package uk.ac.wellcome.platform.ingestor.common.fixtures

import com.sksamuel.elastic4s.{ElasticClient, Index}
import io.circe.Decoder
import org.scalatest.Suite
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.pipeline_storage.Indexer
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.platform.ingestor.common.services.IngestorWorkerService
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait IngestorFixtures
    extends ElasticIndexerFixtures
    with BigMessagingFixture
    with Akka {
  this: Suite =>

  def withWorkerService[T, R](queue: Queue,
                              index: Index,
                              indexer: Indexer[T],
                              elasticClient: ElasticClient = elasticClient)(
    testWith: TestWith[IngestorWorkerService[T], R])(
    implicit dec: Decoder[T]): R =
    withActorSystem { implicit actorSystem =>
      {
        withBigMessageStream[T, R](queue) { messageStream =>
          val ingestorConfig = IngestorConfig(
            batchSize = 100,
            flushInterval = 1 seconds
          )

          val workerService = new IngestorWorkerService(
            documentIndexer = indexer,
            ingestorConfig = ingestorConfig,
            messageStream = messageStream
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }
}
