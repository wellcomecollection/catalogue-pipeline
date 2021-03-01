package weco.catalogue.sierra_indexer.fixtures

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse
import org.scalatest.{Assertion, Suite}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.NoStrictMapping
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryTypedStore
import weco.catalogue.sierra_adapter.models.SierraTransformable
import weco.catalogue.sierra_indexer.services.Worker

import scala.concurrent.ExecutionContext.Implicits.global

trait IndexerFixtures
  extends ElasticsearchFixtures
    with Eventually
    with IntegrationPatience
    with Akka
    with SQS { this: Suite =>
  def withWorker[R](
    queue: Queue, typedStore: MemoryTypedStore[S3ObjectLocation, SierraTransformable],
    indexPrefix: String)(
    testWith: TestWith[Worker, R]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        implicit val client: ElasticClient = elasticClient

        val worker = new Worker(sqsStream, typedStore, indexPrefix)

        worker.run()

        testWith(worker)
      }
    }

  def withBibIndexes[R](testWith: TestWith[String, R]): R = {
    val indexPrefix = s"sierra_${randomAlphanumeric()}".toLowerCase()

    withLocalElasticsearchIndex(NoStrictMapping, index = Index(s"${indexPrefix}_bibs")) { _ =>
      withLocalElasticsearchIndex(NoStrictMapping, index = Index(s"${indexPrefix}_varfields")) { _ =>
        withLocalElasticsearchIndex(NoStrictMapping, index = Index(s"${indexPrefix}_fixedfields")) { _ =>
          testWith(indexPrefix)
        }
      }
    }
  }

  def assertElasticsearchEventuallyHas(index: Index, id: String, json: String): Assertion =
    eventually {
      val response: Response[GetResponse] = elasticClient.execute {
        get(index, id)
      }.await

      val getResponse = response.result

      getResponse.exists shouldBe true

      assertJsonStringsAreEqualIgnoringNulls(getResponse.sourceAsString, json)
    }
}
