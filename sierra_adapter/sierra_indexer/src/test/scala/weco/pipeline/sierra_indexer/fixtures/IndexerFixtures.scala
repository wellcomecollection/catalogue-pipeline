package weco.pipeline.sierra_indexer.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse
import org.scalatest.{Assertion, Suite}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import weco.pekko.fixtures.Pekko
import weco.elasticsearch.IndexConfig
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.sns.NotificationMessage
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.pipeline.sierra_indexer.services.Worker

trait IndexerFixtures
    extends ElasticsearchFixtures
    with Eventually
    with IntegrationPatience
    with Pekko
    with SQS { this: Suite =>
  def withWorker[R](
    queue: Queue =
      Queue("test://q", "arn::test:q", visibilityTimeout = 1 seconds),
    typedStore: MemoryTypedStore[S3ObjectLocation, SierraTransformable],
    indexPrefix: String
  )(
    testWith: TestWith[Worker, R]
  ): R =
    withActorSystem {
      implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) {
          sqsStream =>
            val worker = new Worker(sqsStream, typedStore, indexPrefix)

            worker.run()

            testWith(worker)
        }
    }

  private def withIndex[R](prefix: String, suffix: String)(
    testWith: TestWith[Index, R]
  ): R = {
    val index = Index(s"${prefix}_$suffix")

    withLocalElasticsearchIndex(IndexConfig.empty, index = index) {
      testWith(_)
    }
  }

  def withIndices[R](testWith: TestWith[String, R]): R = {
    val indexPrefix = s"sierra_${randomAlphanumeric()}".toLowerCase()

    withIndex(indexPrefix, "bibs") {
      _ =>
        withIndex(indexPrefix, "items") {
          _ =>
            withIndex(indexPrefix, "holdings") {
              _ =>
                withIndex(indexPrefix, "varfields") {
                  _ =>
                    withIndex(indexPrefix, "fixedfields") {
                      _ =>
                        testWith(indexPrefix)
                    }
                }
            }
        }
    }
  }

  def assertElasticsearchEventuallyHas(
    index: Index,
    id: String,
    json: String
  ): Assertion =
    eventually {
      val response: Response[GetResponse] = elasticClient.execute {
        get(index, id)
      }.await

      val getResponse = response.result

      getResponse.exists shouldBe true

      assertJsonStringsAreEqualIgnoringNulls(getResponse.sourceAsString, json)
    }
}
