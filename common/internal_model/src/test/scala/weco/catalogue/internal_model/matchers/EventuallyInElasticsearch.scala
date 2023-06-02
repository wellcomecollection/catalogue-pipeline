package weco.catalogue.internal_model.matchers

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import io.circe.{Decoder, Encoder}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.Assertion
import weco.elasticsearch.model.IndexId
import weco.json.JsonUtil.{fromJson, toJson}
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.work.{Work, WorkState}

import scala.concurrent.ExecutionContext.Implicits.global

//TODO: Consider turning this into a matcher?

//TODO: Change this to use the REST API directly

trait EventuallyInElasticsearch
    extends Eventually
    with ScalaFutures
    with JsonStringIgnoringNullsMatcher
    with IntegrationPatience
    with Matchers {

  val elasticClient: ElasticClient

  def assertElasticsearchEventuallyHas[T](
    index: Index,
    documents: T*
  )(implicit id: IndexId[T], encoder: Encoder[T]): Seq[Assertion] =
    documents.map {
      document =>
        val documentJson = toJson(document).get

        eventually {
          val response: Response[GetResponse] = elasticClient.execute {
            get(index, id.indexId(document))
          }.await

          val getResponse = response.result

          assert(
            getResponse.exists,
            s"Document ${id.indexId(document)} does not exist in the GET response"
          )
          getResponse.sourceAsString should beTheSameJsonIgnoringNulls(
            documentJson
          )
        }
    }

  protected def assertElasticsearchEventuallyHasWork[State <: WorkState](
    index: Index,
    works: Work[State]*
  )(implicit enc: Encoder[Work[State]]): Seq[Assertion] = {
    implicit val id: IndexId[Work[State]] =
      (work: Work[State]) => work.id
    assertElasticsearchEventuallyHas(index, works: _*)
  }

  protected def assertElasticsearchEventuallyHasImage[State <: ImageState](
    index: Index,
    images: Image[State]*
  )(implicit enc: Encoder[Image[State]]): Seq[Assertion] = {
    implicit val id: IndexId[Image[State]] =
      (image: Image[State]) => image.id
    assertElasticsearchEventuallyHas(index, images: _*)
  }

  protected def assertElasticsearchEmpty(index: Index): Assertion = {
    // Elasticsearch is eventually consistent so, when the future completes,
    // the documents won't appear in the search until after a refresh
    shouldHaveCount(index, 0)
  }

  protected def shouldHaveCount(index: Index, expectedCount: Int): Assertion = {
    eventually {
      val countFuture = elasticClient.execute {
        count(index.name)
      }

      whenReady(countFuture) {
        _.result.count shouldBe expectedCount
      }
    }

  }

  protected def matchAll(
    index: Index,
    expectedCount: Int
  ): Array[SearchHit] = {
    eventually {
      val response: Response[SearchResponse] = elasticClient.execute {
        search(index).matchAllQuery()
      }.await

      val hits = response.result.hits.hits

      hits should have size expectedCount
      hits
    }
  }

  protected def matchAllAsMaps(
    index: Index,
    expectedCount: Int
  ): List[Map[String, AnyRef]] = {
    matchAll(index, expectedCount).map(_.sourceAsMap).toList
  }

  protected def matchAllAsJsonStrings(
    index: Index,
    expectedCount: Int
  ): List[String] = {
    matchAll(index, expectedCount)
      .map(_.sourceAsString)
      .toList
  }

  protected def matchAllAsDocuments[Doc](
    index: Index,
    expectedCount: Int
  )(implicit decoder: Decoder[Doc]): List[Doc] = {
    matchAllAsJsonStrings(index, expectedCount).map(fromJson[Doc](_).get)
  }

}
