package weco.pipeline.calm_indexer.fixtures

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.{Index, Response}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, Suite}
import weco.akka.fixtures.Akka
import weco.catalogue.source_model.calm.CalmRecord
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.sns.NotificationMessage
import weco.pipeline.calm_indexer.services.Worker
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import scala.concurrent.ExecutionContext.Implicits.global

trait IndexerFixtures
    extends ElasticsearchFixtures
    with Eventually
    with IntegrationPatience
    with Akka
    with SQS { this: Suite =>
  def withWorker[R](queue: Queue,
                    typedStore: MemoryTypedStore[S3ObjectLocation, CalmRecord],
                    index: Index)(
    testWith: TestWith[Worker, R]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val worker = new Worker(sqsStream, typedStore, index)

        worker.run()

        testWith(worker)
      }
    }

  def assertElasticsearchEventuallyHas(index: Index,
                                       id: String,
                                       json: String): Assertion =
    eventually {
      val response: Response[GetResponse] = elasticClient.execute {
        get(index, id)
      }.await

      val getResponse = response.result

      getResponse.exists shouldBe true

      assertJsonStringsAreEqualIgnoringNulls(getResponse.sourceAsString, json)
    }
}
