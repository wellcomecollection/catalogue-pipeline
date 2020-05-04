package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.model.Uri
import io.circe.generic.extras.JsonKey
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.{Encoder, Json}
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.models.work.internal.{AugmentedImage, IdentifiedWork}
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.services.{
  ImagesSearchOptions,
  WorksSearchOptions
}

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
  description = "A paginated list of documents."
)
case class DisplayResultList[DisplayResult, DisplayAggs](
  @JsonKey("@context") context: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "ResultList",
  pageSize: Int,
  totalPages: Int,
  totalResults: Int,
  results: List[DisplayResult],
  prevPage: Option[String] = None,
  nextPage: Option[String] = None,
  aggregations: Option[DisplayAggs] = None
)

object DisplayResultList {
  implicit def encoder[R: Encoder, A: Encoder]
    : Encoder[DisplayResultList[R, A]] = deriveEncoder

  def apply(
    resultList: ResultList[IdentifiedWork, Aggregations],
    searchOptions: WorksSearchOptions,
    includes: WorksIncludes,
    requestUri: Uri,
    contextUri: String): DisplayResultList[DisplayWork, DisplayAggregations] = {
    val totalPages =
      getTotalPages(resultList.totalResults, searchOptions.pageSize)
    val prevPage =
      pageLink(searchOptions.pageNumber - 1, totalPages, requestUri)
    val nextPage =
      pageLink(searchOptions.pageNumber + 1, totalPages, requestUri)
    DisplayResultList(
      context = contextUri,
      pageSize = searchOptions.pageSize,
      totalPages = totalPages,
      totalResults = resultList.totalResults,
      results = resultList.results.map(DisplayWork.apply(_, includes)),
      prevPage = prevPage,
      nextPage = nextPage,
      aggregations = resultList.aggregations.map(DisplayAggregations.apply)
    )
  }

  def apply(resultList: ResultList[AugmentedImage, Unit],
            searchOptions: ImagesSearchOptions,
            requestUri: Uri,
            contextUri: String): DisplayResultList[DisplayImage, Unit] = {
    val totalPages =
      getTotalPages(resultList.totalResults, searchOptions.pageSize)
    val prevPage =
      pageLink(searchOptions.pageNumber - 1, totalPages, requestUri)
    val nextPage =
      pageLink(searchOptions.pageNumber + 1, totalPages, requestUri)
    DisplayResultList(
      context = contextUri,
      pageSize = searchOptions.pageSize,
      totalPages = totalPages,
      totalResults = resultList.totalResults,
      results = resultList.results.map(DisplayImage.apply),
      prevPage = prevPage,
      nextPage = nextPage,
      aggregations = resultList.aggregations
    )
  }

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

  private def getTotalPages(totalResults: Int, pageSize: Int): Int =
    Math.ceil(totalResults.toDouble / pageSize).toInt
}
