package uk.ac.wellcome.platform.api.rest

import java.time.LocalDate

import io.circe.Decoder
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.services.WorksSearchOptions

case class SingleWorkParams(
  include: Option[WorksIncludes],
  _expandPaths: Option[List[String]],
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
        "include".as[WorksIncludes].?,
        "_expandPaths".as[List[String]].?,
        "_index".as[String].?
      )
    ).tmap((SingleWorkParams.apply _).tupled(_))

  implicit val decodePaths: Decoder[List[String]] =
    decodeCommaSeparated

  implicit val includesDecoder: Decoder[WorksIncludes] =
    decodeOneOfCommaSeparated(
      "identifiers" -> WorkInclude.Identifiers,
      "items" -> WorkInclude.Items,
      "subjects" -> WorkInclude.Subjects,
      "genres" -> WorkInclude.Genres,
      "contributors" -> WorkInclude.Contributors,
      "production" -> WorkInclude.Production,
      "notes" -> WorkInclude.Notes,
      "collection" -> WorkInclude.Collection,
    ).emap(values => Right(WorksIncludes(values)))
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
  include: Option[WorksIncludes],
  aggregations: Option[List[AggregationRequest]],
  sort: Option[List[SortRequest]],
  sortOrder: Option[SortingOrder],
  query: Option[String],
  collection: Option[CollectionPathFilter],
  `collection.depth`: Option[CollectionDepthFilter],
  _queryType: Option[SearchQueryType],
  _index: Option[String],
) extends QueryParams
    with Paginated {

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

  private def filters: List[DocumentFilter] =
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
  import SingleWorkParams.includesDecoder
  import CommonDecoders.licenseFilter

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
        "include".as[WorksIncludes].?,
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
      validated(params.paginationErrors, params)
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
