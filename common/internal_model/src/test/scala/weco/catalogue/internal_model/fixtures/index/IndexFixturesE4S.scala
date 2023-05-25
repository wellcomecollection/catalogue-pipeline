package weco.catalogue.internal_model.fixtures.index

import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import com.sksamuel.elastic4s.requests.common.VersionType.ExternalGte
import com.sksamuel.elastic4s.requests.indexes.admin.IndexExistsResponse
import io.circe.Encoder
import org.elasticsearch.client.RestClient
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import org.scalatest.Assertion
import weco.json.JsonUtil.toJson
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.work.{Work, WorkState}
import org.scalatest.matchers.should.Matchers

/*
 * Temporary shimmy kind of thing that still uses Elastic4s to do some elasticy things
 *
 * These are just copied out of the existing code and are waiting to be rewritten.
 *
 * When rewritten to directly use the Rest client, put them in the appropriate
 * trait (IndexFixturesBase or ElasticearchFixtures)
 * Eventually, this trait will be empty and can be deleted
 */
trait IndexFixturesE4S extends Eventually with ScalaFutures with Matchers {
  protected val restClient: RestClient

  // TODO: Eventually stop using ElasticClient (when the application switches to REST client)
  implicit val elasticClient: ElasticClient = ElasticClient(
    JavaClient.fromRestClient(restClient)
  )

  protected def createIndexName: String
  def createIndex: Index =
    Index(name = createIndexName)

  def insertIntoElasticsearch[State <: WorkState](
    index: Index,
    works: Work[State]*
  )(implicit encoder: Encoder[Work[State]]): Assertion = {
    val result = elasticClient.execute(
      bulk(
        works.map {
          work =>
            val jsonDoc = toJson(work).get
            indexInto(index.name)
              .version(work.version)
              .versionType(ExternalGte)
              .id(work.id)
              .doc(jsonDoc)
        }
      ).refreshImmediately
    )

    // With a large number of works this can take a long time
    // 30 seconds should be enough
    whenReady(result, Timeout(Span(30, Seconds))) {
      _ =>
        getSizeOf(index) shouldBe works.size
    }
  }

  def insertImagesIntoElasticsearch[State <: ImageState](
    index: Index,
    images: Image[State]*
  )(implicit encoder: Encoder[Image[State]]): Assertion = {
    val result = elasticClient.execute(
      bulk(
        images.map {
          image =>
            val jsonDoc = toJson(image).get

            indexInto(index.name)
              .version(image.version)
              .versionType(ExternalGte)
              .id(image.id)
              .doc(jsonDoc)
        }
      ).refreshImmediately
    )

    whenReady(result) {
      _ =>
        getSizeOf(index) shouldBe images.size
    }
  }

  def getSizeOf(index: Index): Long =
    elasticClient
      .execute {
        count(index.name)
      }
      .await
      .result
      .count

  private def indexDoesExist(index: Index): Boolean = {
    val response: Response[IndexExistsResponse] =
      elasticClient
        .execute(indexExists(index.name))
        .await

    response.result.exists
  }

  def eventuallyIndexExists(index: Index): Assertion =
    eventually {
      assert(
        indexDoesExist(index),
        s"Index $index does not exist"
      )
    }

}
