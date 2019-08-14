package uk.ac.wellcome.platform.ingestor.fixtures

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticClient
import org.scalatest.Suite
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork
import uk.ac.wellcome.platform.ingestor.config.models.IngestorConfig
import uk.ac.wellcome.platform.ingestor.services.IngestorWorkerService
import uk.ac.wellcome.storage.streaming.CodecInstances._

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
      withMessageStream[IdentifiedBaseWork, R](queue) { messageStream =>
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
