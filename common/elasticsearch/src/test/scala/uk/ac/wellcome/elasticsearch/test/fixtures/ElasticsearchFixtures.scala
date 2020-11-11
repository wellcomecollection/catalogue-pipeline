package uk.ac.wellcome.elasticsearch.test.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Assertion, Suite}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.cluster.ClusterHealthResponse
import com.sksamuel.elastic4s.requests.common.VersionType.ExternalGte
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.indexes.IndexResponse
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import grizzled.slf4j.Logging
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.parse

import uk.ac.wellcome.elasticsearch._
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.json.JsonUtil.{fromJson, toJson}
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

trait ElasticsearchFixtures
    extends Eventually
    with ScalaFutures
    with Matchers
    with JsonAssertions
    with IntegrationPatience
    with Logging
    with RandomGenerators { this: Suite =>

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
  eventually(Timeout(Span(40, Seconds))) {
    val response: Response[ClusterHealthResponse] = elasticClient
      .execute(clusterHealth())
      .await

    response.result.numberOfNodes shouldBe 1
  }

  def withLocalIndices[R](testWith: TestWith[ElasticConfig, R]): R =
    withLocalWorksIndex { worksIndex =>
      withLocalImagesIndex { imagesIndex =>
        testWith(ElasticConfig(worksIndex, imagesIndex))
      }
    }

  def withLocalWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = IdentifiedWorkIndexConfig) {
      index =>
        testWith(index)
    }

  def withLocalSourceWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = SourceWorkIndexConfig) { index =>
      testWith(index)
    }

  def withLocalMergedWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = MergedWorkIndexConfig) { index =>
      testWith(index)
    }

  def withLocalDenormalisedWorksIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = DenormalisedWorkIndexConfig) {
      index =>
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
    works: Work[Identified]*): Seq[Assertion] = {
    implicit val id: CanonicalId[Work[Identified]] =
      (work: Work[Identified]) => work.state.canonicalId
    assertElasticsearchEventuallyHas(index, works: _*)
  }

  def assertElasticsearchEventuallyHasImage(
    index: Index,
    images: AugmentedImage*): Seq[Assertion] = {
    implicit val id: CanonicalId[AugmentedImage] =
      (image: AugmentedImage) => image.id.canonicalId
    assertElasticsearchEventuallyHas(index, images: _*)
  }

  def assertElasticsearchEventuallyHas[T](index: Index, documents: T*)(
    implicit id: CanonicalId[T],
    encoder: Encoder[T]): Seq[Assertion] =
    documents.map { document =>
      val documentJson = toJson(document).get

      eventually {
        val response: Response[GetResponse] = elasticClient.execute {
          get(index, id.canonicalId(document))
        }.await

        val getResponse = response.result

        getResponse.exists shouldBe true

        assertJsonStringsAreEqualIgnoringNulls(
          getResponse.sourceAsString,
          documentJson)
      }
    }

  def assertObjectIndexed[T](index: Index, t: T)(
    implicit decoder: Decoder[T]): Assertion =
    // Elasticsearch is eventually consistent so, when the future completes,
    // the documents won't appear in the search until after a refresh
    eventually {
      val response: Response[SearchResponse] = elasticClient.execute {
        search(index).matchAllQuery()
      }.await

      val hits = response.result.hits.hits

      hits should have size 1
      fromJson[T](hits.head.sourceAsString).get shouldEqual t
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
                                      works: Work[Identified]*): Unit = {
    implicit val id: CanonicalId[Work[Identified]] =
      (work: Work[Identified]) => work.state.canonicalId
    assertElasticsearchNeverHas(index, works: _*)
  }

  def assertElasticsearchNeverHas[T](index: Index, documents: T*)(
    implicit id: CanonicalId[T]): Unit = {
    // Let enough time pass to account for elasticsearch
    // eventual consistency before asserting
    Thread.sleep(500)

    documents.foreach { document =>
      val response: Response[GetResponse] = elasticClient
        .execute(get(index, id.canonicalId(document)))
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

  def insertIntoElasticsearch[State <: WorkState](
    index: Index,
    works: Work[State]*)(implicit encoder: Encoder[Work[State]]): Assertion = {
    val result = elasticClient.execute(
      bulk(
        works.map { work =>
          val jsonDoc = toJson(work).get
          indexInto(index.name)
            .version(work.version)
            .versionType(ExternalGte)
            .id(work.id)
            .doc(jsonDoc)
        }
      )
    )

    whenReady(result) { _ =>
      eventually {
        getSizeOf(index) shouldBe works.size
      }
    }
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

    whenReady(result) { _ =>
      eventually {
        getSizeOf(index) shouldBe images.size
      }
    }
  }

  private def getSizeOf(index: Index): Long =
    elasticClient
      .execute { count(index.name) }
      .await
      .result.count

  def createIndex: Index =
    Index(name = createIndexName)

  def createIndexName: String =
    s"index-${randomAlphanumeric().toLowerCase}"

  def assertJsonStringsAreEqualIgnoringNulls(a: String,
                                             b: String): Assertion = {
    val jsonA = parseOrElse(a)
    val jsonB = parseOrElse(a)
    jsonA shouldBe jsonB
  }

  private def parseOrElse(jsonString: String): Json =
    parse(jsonString) match {
      case Right(json) => json.deepDropNullValues
      case Left(err) => {
        println(s"Error trying to parse string <<$jsonString>>")
        throw err
      }
    }
}
