package weco.catalogue.internal_model.matchers

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.Assertion
import weco.elasticsearch.model.IndexId
import weco.json.JsonUtil.toJson
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

  def assertElasticsearchEventuallyHasWork[State <: WorkState](
    index: Index,
    works: Work[State]*
  )(implicit enc: Encoder[Work[State]]): Seq[Assertion] = {
    implicit val id: IndexId[Work[State]] =
      (work: Work[State]) => work.id
    assertElasticsearchEventuallyHas(index, works: _*)
  }

  def assertElasticsearchEventuallyHasImage[State <: ImageState](
    index: Index,
    images: Image[State]*
  )(implicit enc: Encoder[Image[State]]): Seq[Assertion] = {
    implicit val id: IndexId[Image[State]] =
      (image: Image[State]) => image.id
    assertElasticsearchEventuallyHas(index, images: _*)
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

}
