package uk.ac.wellcome.elasticsearch.test.fixtures

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.sksamuel.elastic4s.requests.cluster.ClusterHealthResponse
import com.sksamuel.elastic4s.requests.common.VersionType.ExternalGte
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.indexes.IndexResponse
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, Response}
import grizzled.slf4j.Logging
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
import uk.ac.wellcome.models.work.internal.{AugmentedImage, IdentifiedBaseWork}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

trait ElasticsearchFixtures
    extends Eventually
    with ScalaFutures
    with Matchers
    with JsonAssertions
    with IntegrationPatience
    with Logging { this: Suite =>

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

  def withLocalIndices[R](testWith: TestWith[ElasticConfig, R]): R =
    withLocalWorksIndex { worksIndex =>
      withLocalImagesIndex { imagesIndex =>
        testWith(ElasticConfig(worksIndex, imagesIndex))
      }
    }

  def withLocalWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = WorksIndexConfig) { index =>
      testWith(index)
    }

  def withLocalImagesIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = ImagesIndexConfig) { index =>
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

  def assertObjectIndexed[T](index: Index, t: T)(
    implicit encoder: Encoder[T]): Assertion =
    // Elasticsearch is eventually consistent so, when the future completes,
    // the documents won't appear in the search until after a refresh
    eventually {
      val response: Response[SearchResponse] = elasticClient.execute {
        search(index).matchAllQuery()
      }.await

      val hits = response.result.hits.hits

      hits should have size 1
      assertJsonStringsAreEqual(hits.head.sourceAsString, toJson(t).get)
    }

  def assertElasticsearchEmpty[T](index: Index): Assertion =
    // Elasticsearch is eventually consistent so, when the future completes,
    // the documents won't appear in the search until after a refresh
    eventually {
      val response: Response[SearchResponse] = elasticClient.execute {
        search(index).matchAllQuery()
      }.await

      val hits = response.result.hits.hits

      hits should have size 0
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

  def indexObject[T](index: Index, t: T)(
    implicit encoder: Encoder[T]): Future[Response[IndexResponse]] = {
    val doc = toJson(t).get
    debug(s"ingesting: $doc")
    elasticClient
      .execute {

        indexInto(index.name).doc(doc)
      }
      .map { r =>
        if (r.isError) {
          error(s"Error from Elasticsearch: $r")
        }
        r
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
    assertInserted(result, works, index)
  }

  def insertImagesIntoElasticsearch(index: Index,
                                    images: AugmentedImage*): Assertion = {
    val result = elasticClient.execute(
      bulk(
        images.map { image =>
          val jsonDoc = toJson(image).get

          indexInto(index.name)
            .version(image.version)
            .versionType(ExternalGte)
            .id(image.id.canonicalId)
            .doc(jsonDoc)
        }
      )
    )
    assertInserted(result, images, index)
  }

  private def assertInserted[T](result: Future[Response[BulkResponse]],
                                docs: Seq[T],
                                index: Index): Assertion =
    whenReady(result) { _ =>
      eventually {
        val response: Response[SearchResponse] = elasticClient.execute {
          search(index.name).matchAllQuery().trackTotalHits(true)
        }.await
        response.result.totalHits shouldBe docs.size
      }
    }

  def createIndex: Index =
    Index(name = (Random.alphanumeric take 10 mkString) toLowerCase)
}
