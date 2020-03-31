package uk.ac.wellcome.elasticsearch.test.fixtures

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.cluster.ClusterHealthResponse
import com.sksamuel.elastic4s.requests.common.VersionType.ExternalGte
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, Response}
import io.circe.Encoder
import org.scalactic.source.Position
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Assertion, Matchers, Suite}
import uk.ac.wellcome.elasticsearch._
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait ElasticsearchFixtures
    extends Eventually
    with ScalaFutures
    with Matchers
    with JsonAssertions
    with IntegrationPatience { this: Suite =>

  private val esHost = "localhost"
  private val esPort = 9200

  val elasticClient: ElasticClient = ElasticClientBuilder.create(
    hostname = esHost,
    port = esPort,
    protocol = "http",
    username = "elastic",
    password = "changeme"
  )

  // Elasticsearch takes a while to start up so check that it actually started
  // before running tests.
  eventually {
    val response: Response[ClusterHealthResponse] = elasticClient
      .execute(clusterHealth())
      .await

    response.result.numberOfNodes shouldBe 1
  }(
    PatienceConfig(
      timeout = scaled(Span(40, Seconds)),
      interval = scaled(Span(150, Millis))
    ),
    implicitly[Position])

  def withLocalWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = WorksIndexConfig) { index =>
      testWith(index)
    }

  def withLocalElasticsearchIndex[R](
    config: IndexConfig,
    index: Index = createIndex): Fixture[Index, R] = fixture[Index, R](
    create = {
      new ElasticsearchIndexCreator(
        elasticClient = elasticClient,
        index = index,
        config = config).create.await

      // Elasticsearch is eventually consistent, so the future
      // completing doesn't actually mean that the index exists yet
      eventuallyIndexExists(index)

      index
    },
    destroy = { index =>
      elasticClient.execute(deleteIndex(index.name))
    }
  )

  def eventuallyIndexExists(index: Index): Assertion =
    eventually {
      val response: Response[IndexExistsResponse] =
        elasticClient
          .execute(indexExists(index.name))
          .await

      response.result.isExists shouldBe true
    }

  def eventuallyDeleteIndex(index: Index): Assertion = {
    elasticClient.execute(deleteIndex(index.name))

    eventually {
      val response: Response[IndexExistsResponse] =
        elasticClient
          .execute(indexExists(index.name))
          .await

      response.result.isExists shouldBe false
    }
  }

  def assertElasticsearchEventuallyHasWork(
    index: Index,
    works: IdentifiedBaseWork*): Seq[Assertion] = {
    implicit val id: CanonicalId[IdentifiedBaseWork] =
      (t: IdentifiedBaseWork) => t.canonicalId
    assertElasticsearchEventuallyHas(index, works: _*)
  }

  def assertElasticsearchEventuallyHas[T](index: Index, documents: T*)(
    implicit id: CanonicalId[T],
    encoder: Encoder[T]): Seq[Assertion] =
    documents.map { document =>
      val documentJson = toJson(document).get

      eventually {
        val response: Response[GetResponse] = elasticClient.execute {
          get(id.canonicalId(document)).from(index.name)
        }.await

        val getResponse = response.result

        getResponse.exists shouldBe true

        assertJsonStringsAreEqual(getResponse.sourceAsString, documentJson)
      }
    }

  def assertElasticsearchNeverHasWork(index: Index,
                                      works: IdentifiedBaseWork*): Unit = {
    implicit val id: CanonicalId[IdentifiedBaseWork] =
      (t: IdentifiedBaseWork) => t.canonicalId
    assertElasticsearchNeverHas(index, works: _*)
  }

  def assertElasticsearchNeverHas[T](index: Index, documents: T*)(
    implicit id: CanonicalId[T]): Unit = {
    // Let enough time pass to account for elasticsearch
    // eventual consistency before asserting
    Thread.sleep(500)

    documents.foreach { document =>
      val response: Response[GetResponse] = elasticClient
        .execute(get(id.canonicalId(document)).from(index.name))
        .await

      response.result.found shouldBe false
    }
  }

  def insertIntoElasticsearch(index: Index,
                              works: IdentifiedBaseWork*): Assertion = {
    val result = elasticClient.execute(
      bulk(
        works.map { work =>
          val jsonDoc = toJson(work).get

          indexInto(index.name)
            .version(work.version)
            .versionType(ExternalGte)
            .id(work.canonicalId)
            .doc(jsonDoc)
        }
      )
    )

    whenReady(result) { _ =>
      eventually {
        val response: Response[SearchResponse] = elasticClient.execute {
          search(index.name).matchAllQuery().trackTotalHits(true)
        }.await
        response.result.totalHits shouldBe works.size
      }
    }
  }

  def createIndex: Index =
    Index(name = (Random.alphanumeric take 10 mkString) toLowerCase)
}
