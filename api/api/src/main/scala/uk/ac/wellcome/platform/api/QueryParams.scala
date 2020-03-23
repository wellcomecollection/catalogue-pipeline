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
  expandPaths: Option[List[String]],
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
        "expandPaths".as[List[String]].?,
        "_index".as[String].?
      )
    ).tmap((SingleWorkParams.apply _).tupled(_))

  implicit val decodePaths: Decoder[List[String]] =
    decodeCommaSeparated
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
  license: Option[LicenseFilter],
  include: Option[V2WorksIncludes],
  aggregations: Option[List[AggregationRequest]],
  sort: Option[List[SortRequest]],
  sortOrder: Option[SortingOrder],
  query: Option[String],
  collection: Option[CollectionPathFilter],
  `collection.depth`: Option[CollectionDepthFilter],
  _queryType: Option[SearchQueryType],
  _index: Option[String],
) extends QueryParams {

  def searchOptions(apiConfig: ApiConfig): WorksSearchOptions =
    WorksSearchOptions(
      searchQuery = query map { query =>
        SearchQuery(query, _queryType)
      },
      filters = filters,
      pageSize = pageSize.getOrElse(apiConfig.defaultPageSize),
      pageNumber = page.getOrElse(1),
      aggregations = aggregations.getOrElse(Nil),
      sortBy = sort.getOrElse(Nil),
      sortOrder = sortOrder.getOrElse(SortingOrder.Ascending),
    )

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
      collection,
      `collection.depth`,
      license
    ).flatten

  private def dateFilter: Option[DateRangeFilter] =
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
        "license".as[LicenseFilter].?,
        "include".as[V2WorksIncludes].?,
        "aggregations".as[List[AggregationRequest]].?,
        "sort".as[List[SortRequest]].?,
        "sortOrder".as[SortingOrder].?,
        "query".as[String].?,
        "collection".as[CollectionPathFilter].?,
        "collection.depth".as[CollectionDepthFilter].?,
        "_queryType".as[SearchQueryType].?,
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
    decodeCommaSeparated.emap(strs => Right(WorkTypeFilter(strs)))

  implicit val itemLocationTypeFilter: Decoder[ItemLocationTypeFilter] =
    decodeCommaSeparated.emap(strs => Right(ItemLocationTypeFilter(strs)))

  implicit val languageFilter: Decoder[LanguageFilter] =
    decodeCommaSeparated.emap(strs => Right(LanguageFilter(strs)))

  implicit val genreFilter: Decoder[GenreFilter] =
    Decoder.decodeString.emap(str => Right(GenreFilter(str)))

  implicit val subjectFilter: Decoder[SubjectFilter] =
    Decoder.decodeString.emap(str => Right(SubjectFilter(str)))

  implicit val licenseFilter: Decoder[LicenseFilter] =
    decodeCommaSeparated.emap(strs => Right(LicenseFilter(strs)))

  implicit val collectionsPathFilter: Decoder[CollectionPathFilter] =
    Decoder.decodeString.emap(str => Right(CollectionPathFilter(str)))

  implicit val collectionsDepthFilter: Decoder[CollectionDepthFilter] =
    decodeInt map CollectionDepthFilter

  implicit val aggregationsDecoder: Decoder[List[AggregationRequest]] =
    decodeOneOfCommaSeparated(
      "workType" -> AggregationRequest.WorkType,
      "genres" -> AggregationRequest.Genre,
      "production.dates" -> AggregationRequest.ProductionDate,
      "subjects" -> AggregationRequest.Subject,
      "language" -> AggregationRequest.Language,
      "license" -> AggregationRequest.License,
    )

  implicit val sortDecoder: Decoder[List[SortRequest]] =
    decodeOneOfCommaSeparated(
      "production.dates" -> ProductionDateSortRequest
    )

  implicit val sortOrderDecoder: Decoder[SortingOrder] =
    decodeOneOf(
      "asc" -> SortingOrder.Ascending,
      "desc" -> SortingOrder.Descending,
    )

  implicit val _queryTypeDecoder: Decoder[SearchQueryType] =
    decodeOneWithDefaultOf(
      SearchQueryType.default,
      "BoolBoosted" -> SearchQueryType.BoolBoosted,
      "PhraserBeam" -> SearchQueryType.PhraserBeam,
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
    decodeOneOfCommaSeparated(
      "identifiers" -> WorkInclude.Identifiers,
      "items" -> WorkInclude.Items,
      "subjects" -> WorkInclude.Subjects,
      "genres" -> WorkInclude.Genres,
      "contributors" -> WorkInclude.Contributors,
      "production" -> WorkInclude.Production,
      "notes" -> WorkInclude.Notes,
      "collection" -> WorkInclude.Collection,
    ).emap(values => Right(V2WorksIncludes(values)))

  implicit val decodeLocalDate: Decoder[LocalDate] =
    decodeLocalDateDefault.withErrorMessage(
      "Invalid date encoding. Expected YYYY-MM-DD"
    )

  implicit val decodeInt: Decoder[Int] =
    Decoder.decodeInt.withErrorMessage("must be a valid Integer")

  def decodeCommaSeparated: Decoder[List[String]] =
    Decoder.decodeString.emap(str => Right(str.split(",").toList))

  def decodeOneOf[T](values: (String, T)*): Decoder[T] =
    Decoder.decodeString.emap { str =>
      values.toMap
        .get(str)
        .map(Right(_))
        .getOrElse(Left(invalidValuesMsg(List(str), values.map(_._1).toList)))
    }

  def decodeOneWithDefaultOf[T](default: T, values: (String, T)*): Decoder[T] =
    Decoder.decodeString.map { values.toMap.getOrElse(_, default) }

  def decodeOneOfCommaSeparated[T](values: (String, T)*): Decoder[List[T]] =
    decodeCommaSeparated.emap { strs =>
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
