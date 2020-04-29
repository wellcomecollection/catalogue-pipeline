package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.model.Uri
import io.circe.generic.extras.JsonKey
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.{Encoder, Json}
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.services.WorksSearchOptions

case class ResultResponse[T: Encoder](
  @JsonKey("@context") context: String,
  result: T
)

object ResultResponse {

  // Flattens the 'result' field into the rest of the object
  implicit def encoder[T: Encoder]: Encoder[ResultResponse[T]] =
    deriveEncoder[ResultResponse[T]].mapJson { json =>
      json.asObject
        .flatMap { obj =>
          obj.toMap
            .get("result")
            .flatMap(_.asObject.map(_.toList))
            .map { fields =>
              Json.obj(fields ++ obj.filterKeys(_ != "result").toList: _*)
            }
        }
        .getOrElse(json)
    }
}

@Schema(
  name = "ResultList",
  description = "A paginated list of works."
)
case class MultipleWorksResponse(
  @JsonKey("@context") context: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String,
  pageSize: Int,
  totalPages: Int,
  totalResults: Int,
  results: List[DisplayWorkV2],
  prevPage: Option[String] = None,
  nextPage: Option[String] = None,
  aggregations: Option[DisplayAggregations] = None
)

object MultipleWorksResponse {
  implicit val encoder: Encoder[MultipleWorksResponse] = deriveEncoder

  def apply(resultList: ResultList,
            searchOptions: WorksSearchOptions,
            includes: V2WorksIncludes,
            requestUri: Uri,
            contextUri: String): MultipleWorksResponse =
    MultipleWorksResponse(
      resultList = DisplayResultList(
        resultList,
        DisplayWorkV2.apply,
        searchOptions.pageSize,
        includes,
      ),
      currentPage = searchOptions.pageNumber,
      requestUri = requestUri,
      contextUri = contextUri
    )

  def apply(resultList: DisplayResultList[DisplayWorkV2],
            currentPage: Int,
            requestUri: Uri,
            contextUri: String): MultipleWorksResponse =
    MultipleWorksResponse(
      context = contextUri,
      ontologyType = resultList.ontologyType,
      pageSize = resultList.pageSize,
      totalPages = resultList.totalPages,
      totalResults = resultList.totalResults,
      results = resultList.results,
      prevPage = pageLink(currentPage - 1, resultList.totalPages, requestUri),
      nextPage = pageLink(currentPage + 1, resultList.totalPages, requestUri),
      aggregations = resultList.aggregations,
    )

  private def pageLink(page: Int,
                       totalPages: Int,
                       requestUri: Uri): Option[String] =
    if (pageInBounds(page, totalPages))
      Some(
        requestUri
          .withQuery(
            pageQuery(page, requestUri.query())
          )
          .toString
      )
    else
      None

  private def pageQuery(page: Int, previousQuery: Uri.Query) =
    Uri.Query(
      previousQuery.toMap.updated("page", page.toString)
    )

  private def pageInBounds(page: Int, totalPages: Int) =
    page > 0 && page <= totalPages
}
