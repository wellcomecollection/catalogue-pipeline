package uk.ac.wellcome.platform.ingestor.works.fixtures

import com.sksamuel.elastic4s.{ElasticClient, Index}
import org.scalatest.Suite
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.platform.ingestor.works.services.IngestorWorkerService
import uk.ac.wellcome.storage.ObjectLocation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait WorkerServiceFixture
    extends ElasticsearchFixtures
    with BigMessagingFixture {
  this: Suite =>
  def withWorkerService[R](queue: Queue,
                           index: Index,
                           elasticClient: ElasticClient = elasticClient)(
    testWith: TestWith[IngestorWorkerService, R]): R =
    withActorSystem { implicit actorSystem =>
      {
        implicit val typedStoreT =
          MemoryTypedStoreCompanion[ObjectLocation, IdentifiedBaseWork]()
        withBigMessageStream[IdentifiedBaseWork, R](queue) { messageStream =>
          val ingestorConfig = IngestorConfig(
            batchSize = 100,
            flushInterval = 5 seconds,
            index = index
          )

          val workerService = new IngestorWorkerService(
            elasticClient = elasticClient,
            ingestorConfig = ingestorConfig,
            messageStream = messageStream
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }
}
