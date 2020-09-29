package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import io.circe.Decoder
import com.sksamuel.elastic4s.{Hit, Index}
import com.sksamuel.elastic4s.ElasticError
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.circe._

import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.rest.{
  PaginatedSearchOptions,
  PaginationQuery
}
import WorkState.Identified

case class WorkQuery(query: String, queryType: SearchQueryType)

class WorksService(searchService: ElasticsearchService)(
  implicit ec: ExecutionContext) {

  def findWorkById(canonicalId: String)(
    index: Index): Future[Either[ElasticError, Option[Work[Identified]]]] =
    searchService
      .executeGet(canonicalId)(index)
      .map { result: Either[ElasticError, GetResponse] =>
        result.map { response: GetResponse =>
          if (response.exists)
            Some(deserialize[Work[Identified]](response))
          else None
        }
      }

  def listOrSearchWorks(index: Index,
                        searchOptions: SearchOptions[WorkFilter]): Future[
    Either[ElasticError, ResultList[Work.Visible[Identified], Aggregations]]] =
    searchService
      .executeSearch(
        searchOptions = searchOptions,
        requestBuilder = WorksRequestBuilder,
        index = index
      )
      .map { _.map(createResultList) }

  private def createResultList(searchResponse: SearchResponse)
    : ResultList[Work.Visible[Identified], Aggregations] = {
    ResultList(
      results = searchResponseToWorks(searchResponse),
      totalResults = searchResponse.totalHits.toInt,
      aggregations = searchResponseToAggregationResults(searchResponse)
    )
  }

  private def searchResponseToWorks(
    searchResponse: SearchResponse): List[Work.Visible[Identified]] =
    searchResponse.hits.hits.map { hit =>
      deserialize[Work.Visible[Identified]](hit)
    }.toList

  private def searchResponseToAggregationResults(
    searchResponse: SearchResponse): Option[Aggregations] = {
    Aggregations(searchResponse)
  }

  private def deserialize[T](hit: Hit)(implicit decoder: Decoder[T]): T =
    hit.safeTo[T] match {
      case Success(work) => work
      case Failure(e) =>
        throw new RuntimeException(
          s"Unable to parse JSON($e): ${hit.sourceAsString}"
        )
    }
}
