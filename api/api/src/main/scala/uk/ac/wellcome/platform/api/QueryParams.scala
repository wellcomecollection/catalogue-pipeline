package uk.ac.wellcome.platform.api

import java.time.LocalDate
import io.circe.Decoder
import io.circe.java8.time.TimeInstances
import akka.http.scaladsl.server.{Directives, ValidationRejection}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import io.circe.{Decoder, Json}

import uk.ac.wellcome.platform.api.services.WorksSearchOptions
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.display.models._

sealed trait QueryParams

case class SingleWorkParams(
  include: Option[V2WorksIncludes],
  _index: Option[String],
) extends QueryParams

object SingleWorkParams extends QueryParamsUtils {

  // This is a custom akka-http directive which extracts SingleWorkParams
  // data from the query string, returning an invalid response when any given
  // parameter is not correctly parsed. More info on custom directives is
  // available here:
  // https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/custom-directives.html
  def parse =
    parameter(
      (
        "include".as[V2WorksIncludes].?,
        "_index".as[String].?
      )
    ).tmap((SingleWorkParams.apply _).tupled(_))
}

case class MultipleWorksParams(
  page: Option[Int],
  pageSize: Option[Int],
  workType: Option[WorkTypeFilter],
  `items.locations.locationType`: Option[ItemLocationTypeFilter],
  `production.dates.from`: Option[LocalDate],
  `production.dates.to`: Option[LocalDate],
  language: Option[LanguageFilter],
  `genres.label`: Option[GenreFilter],
  `subjects.label`: Option[SubjectFilter],
  include: Option[V2WorksIncludes],
  aggregations: Option[List[AggregationRequest]],
  sort: Option[List[SortRequest]],
  sortOrder: Option[SortingOrder],
  query: Option[String],
  _queryType: Option[WorkQueryType],
  _index: Option[String],
) extends QueryParams {

  def searchOptions(apiConfig: ApiConfig): WorksSearchOptions =
    WorksSearchOptions(
      filters = filters,
      pageSize = pageSize.getOrElse(apiConfig.defaultPageSize),
      pageNumber = page.getOrElse(1),
      aggregations = aggregations.getOrElse(Nil),
      sortBy = sort.getOrElse(Nil),
      sortOrder = sortOrder.getOrElse(SortingOrder.Ascending),
    )

  def workQuery: Option[WorkQuery] =
    query.map {qry =>
      WorkQuery(
        queryString = qry,
        queryType = _queryType.getOrElse(WorkQueryType.MSMBoostQuery)
      )
    }

  def validationErrors: List[String] =
    List(
      page
        .filterNot(_ >= 1)
        .map(_ => "page: must be greater than 1"),
      pageSize
        .filterNot(size => size >= 1 && size <= 100)
        .map(_ => "pageSize: must be between 1 and 100")
    ).flatten

  private def filters: List[WorkFilter] =
    List(
      workType,
      `items.locations.locationType`,
      dateFilter,
      language,
      `genres.label`,
      `subjects.label`,
    ).flatten

  private def dateFilter =
    (`production.dates.from`, `production.dates.to`) match {
      case (None, None)       => None
      case (dateFrom, dateTo) => Some(DateRangeFilter(dateFrom, dateTo))
    }
}

object MultipleWorksParams extends QueryParamsUtils {

  // This is a custom akka-http directive which extracts MultipleWorksParams
  // data from the query string, returning an invalid response when any given
  // parameter is not correctly parsed. More info on custom directives is
  // available here:
  // https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/custom-directives.html
  def parse =
    parameter(
      (
        "page".as[Int].?,
        "pageSize".as[Int].?,
        "workType".as[WorkTypeFilter] ?,
        "items.locations.locationType".as[ItemLocationTypeFilter].?,
        "production.dates.from".as[LocalDate].?,
        "production.dates.to".as[LocalDate].?,
        "language".as[LanguageFilter].?,
        "genres.label".as[GenreFilter].?,
        "subjects.label".as[SubjectFilter].?,
        "include".as[V2WorksIncludes].?,
        "aggregations".as[List[AggregationRequest]].?,
        "sort".as[List[SortRequest]].?,
        "sortOrder".as[SortingOrder].?,
        "query".as[String].?,
        "_queryType".as[WorkQueryType].?,
        "_index".as[String].?,
      )
    ).tflatMap { args =>
      val params = (MultipleWorksParams.apply _).tupled(args)
      params.validationErrors match {
        case Nil => provide(params)
        case errs =>
          reject(ValidationRejection(errs.mkString(", ")))
            .toDirective[Tuple1[MultipleWorksParams]]
      }
    }

  implicit val workTypeFilter: Decoder[WorkTypeFilter] =
    decodeCommaSeperated.emap(strs => Right(WorkTypeFilter(strs)))

  implicit val itemLocationTypeFilter: Decoder[ItemLocationTypeFilter] =
    decodeCommaSeperated.emap(strs => Right(ItemLocationTypeFilter(strs)))

  implicit val languageFilter: Decoder[LanguageFilter] =
    decodeCommaSeperated.emap(strs => Right(LanguageFilter(strs)))

  implicit val genreFilter: Decoder[GenreFilter] =
    Decoder.decodeString.emap(str => Right(GenreFilter(str)))

  implicit val subjectFilter: Decoder[SubjectFilter] =
    Decoder.decodeString.emap(str => Right(SubjectFilter(str)))

  implicit val aggregationsDecoder: Decoder[List[AggregationRequest]] =
    decodeOneOfCommaSeperated(
      "workType" -> AggregationRequest.WorkType,
      "genres" -> AggregationRequest.Genre,
      "production.dates" -> AggregationRequest.ProductionDate,
      "subjects" -> AggregationRequest.Subject,
      "language" -> AggregationRequest.Language,
    )

  implicit val sortDecoder: Decoder[List[SortRequest]] =
    decodeOneOfCommaSeperated(
      "production.dates" -> ProductionDateSortRequest
    )

  implicit val sortOrderDecoder: Decoder[SortingOrder] =
    decodeOneOf(
      "asc" -> SortingOrder.Ascending,
      "desc" -> SortingOrder.Descending,
    )

  implicit val workQueryDecoder: Decoder[WorkQueryType] =
    decodeOneOf(
      "default" -> WorkQueryType.MSMBoostQuery,
      "usingAnd" -> WorkQueryType.MSMBoostQueryUsingAndOperator
    )
}

trait QueryParamsUtils extends Directives with TimeInstances {

  implicit def unmarshaller[T](
    implicit decoder: Decoder[T]): Unmarshaller[String, T] =
    Unmarshaller.strict[String, T] { str =>
      decoder.decodeJson(Json.fromString(str)) match {
        case Left(err)    => throw new IllegalArgumentException(err.message)
        case Right(value) => value
      }
    }

  implicit val includesDecoder: Decoder[V2WorksIncludes] =
    decodeOneOfCommaSeperated(
      "identifiers" -> WorkInclude.Identifiers,
      "items" -> WorkInclude.Items,
      "subjects" -> WorkInclude.Subjects,
      "genres" -> WorkInclude.Genres,
      "contributors" -> WorkInclude.Contributors,
      "production" -> WorkInclude.Production,
      "notes" -> WorkInclude.Notes,
      "alternativeTitles" -> WorkInclude.AlternativeTitles,
    ).emap(values => Right(V2WorksIncludes(values)))

  implicit val decodeLocalDate: Decoder[LocalDate] =
    decodeLocalDateDefault.withErrorMessage(
      "Invalid date encoding. Expected YYYY-MM-DD"
    )

  implicit val decodeInt: Decoder[Int] =
    Decoder.decodeInt.withErrorMessage("must be a valid Integer")

  def decodeCommaSeperated: Decoder[List[String]] =
    Decoder.decodeString.emap(str => Right(str.split(",").toList))

  def decodeOneOf[T](values: (String, T)*): Decoder[T] =
    Decoder.decodeString.emap { str =>
      values.toMap
        .get(str)
        .map(Right(_))
        .getOrElse(Left(invalidValuesMsg(List(str), values.map(_._1).toList)))
    }

  def decodeOneOfCommaSeperated[T](values: (String, T)*): Decoder[List[T]] =
    decodeCommaSeperated.emap { strs =>
      val mapping = values.toMap
      val results = strs.map { str =>
        mapping
          .get(str)
          .map(Right(_))
          .getOrElse(Left(str))
      }
      val invalid = results.collect { case Left(error) => error }
      val valid = results.collect { case Right(value)  => value }
      (invalid, valid) match {
        case (Nil, results) => Right(results)
        case (invalidValues, _) =>
          Left(invalidValuesMsg(invalidValues, values.map(_._1).toList))
      }
    }

  def invalidValuesMsg(values: List[String], validValues: List[String]) = {
    val oneOfMsg =
      s"Please choose one of: [${validValues.mkString("'", "', '", "'")}]"
    values match {
      case value :: Nil => s"'$value' is not a valid value. $oneOfMsg"
      case _ =>
        s"${values.mkString("'", "', '", "'")} are not valid values. $oneOfMsg"
    }
  }
}
