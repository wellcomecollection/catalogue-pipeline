package uk.ac.wellcome.platform.api.rest

import java.time.LocalDate
import akka.http.scaladsl.server.Directive
import io.circe.Decoder

import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.WorkType

case class SingleWorkParams(
  include: Option[WorksIncludes],
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
        "_index".as[String].?
      )
    ).tmap((SingleWorkParams.apply _).tupled(_))

  implicit val decodePaths: Decoder[List[String]] =
    decodeCommaSeparated

  implicit val includesDecoder: Decoder[WorksIncludes] =
    decodeOneOfCommaSeparated(
      "identifiers" -> WorkInclude.Identifiers,
      "items" -> WorkInclude.Items,
      "holdings" -> WorkInclude.Holdings,
      "subjects" -> WorkInclude.Subjects,
      "genres" -> WorkInclude.Genres,
      "contributors" -> WorkInclude.Contributors,
      "production" -> WorkInclude.Production,
      "languages" -> WorkInclude.Languages,
      "notes" -> WorkInclude.Notes,
      "images" -> WorkInclude.Images,
      "parts" -> WorkInclude.Parts,
      "partOf" -> WorkInclude.PartOf,
      "precededBy" -> WorkInclude.PrecededBy,
      "succeededBy" -> WorkInclude.SucceededBy,
    ).emap(values => Right(WorksIncludes(values: _*)))
}

case class MultipleWorksParams(
  page: Option[Int],
  pageSize: Option[Int],
  workType: Option[FormatFilter],
  `production.dates.from`: Option[LocalDate],
  `production.dates.to`: Option[LocalDate],
  languages: Option[LanguagesFilter],
  `genres.label`: Option[GenreFilter],
  `subjects.label`: Option[SubjectFilter],
  `contributors.agent.label`: Option[ContributorsFilter],
  `items.locations.license`: Option[LicenseFilter],
  include: Option[WorksIncludes],
  aggregations: Option[List[WorkAggregationRequest]],
  sort: Option[List[SortRequest]],
  sortOrder: Option[SortingOrder],
  query: Option[String],
  identifiers: Option[IdentifiersFilter],
  `items.locations.locationType`: Option[ItemLocationTypeIdFilter],
  `items.locations.accessConditions.status`: Option[AccessStatusFilter],
  `type`: Option[WorkTypeFilter],
  partOf: Option[PartOfFilter],
  availabilities: Option[AvailabilitiesFilter],
  _queryType: Option[SearchQueryType],
  _index: Option[String],
) extends QueryParams
    with Paginated {

  def searchOptions(apiConfig: ApiConfig): WorkSearchOptions =
    WorkSearchOptions(
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

  private def filters: List[WorkFilter] =
    List(
      workType,
      dateFilter,
      languages,
      `genres.label`,
      `subjects.label`,
      `contributors.agent.label`,
      identifiers,
      `items.locations.locationType`,
      `items.locations.accessConditions.status`,
      `items.locations.license`,
      `type`,
      partOf,
      availabilities
    ).flatten

  private def dateFilter: Option[DateRangeFilter] =
    (`production.dates.from`, `production.dates.to`) match {
      case (None, None)       => None
      case (dateFrom, dateTo) => Some(DateRangeFilter(dateFrom, dateTo))
    }
}

object MultipleWorksParams extends QueryParamsUtils {
  import SingleWorkParams.includesDecoder
  import CommonDecoders._

  // This is a custom akka-http directive which extracts MultipleWorksParams
  // data from the query string, returning an invalid response when any given
  // parameter is not correctly parsed. More info on custom directives is
  // available here:
  // https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/custom-directives.html
  def parse: Directive[Tuple1[MultipleWorksParams]] =
    parameter(
      (
        "page".as[Int].?,
        "pageSize".as[Int].?,
        "workType".as[FormatFilter] ?,
        "production.dates.from".as[LocalDate].?,
        "production.dates.to".as[LocalDate].?,
        "languages".as[LanguagesFilter].?,
        "genres.label".as[GenreFilter].?,
        "subjects.label".as[SubjectFilter].?,
        "contributors.agent.label".as[ContributorsFilter].?,
        "license".as[LicenseFilter].?,
        "items.locations.license".as[LicenseFilter].?,
        "include".as[WorksIncludes].?,
        "aggregations".as[List[WorkAggregationRequest]].?,
        "sort".as[List[SortRequest]].?,
        "sortOrder".as[SortingOrder].?,
        "query".as[String].?,
        "identifiers".as[IdentifiersFilter].?,
        "items.locations.locationType".as[ItemLocationTypeIdFilter].?,
        "items.locations.accessConditions.status".as[AccessStatusFilter].?,
        "type".as[WorkTypeFilter].?
      )
    ).tflatMap {
      case (
          page,
          pageSize,
          format,
          dateFrom,
          dateTo,
          languages,
          genre,
          subjects,
          contributors,
          license,
          itemsLocationsLicense,
          includes,
          aggregations,
          sort,
          sortOrder,
          query,
          identifiers,
          itemLocationTypeId,
          accessStatus,
          workType) =>
        // Scala has a max tuple size of 22 so this is nested to get around this limit
        parameter(
          (
            "partOf".as[PartOfFilter].?,
            "availabilities".as[AvailabilitiesFilter].?,
            "_queryType".as[SearchQueryType].?,
            "_index".as[String].?,
          )
        ).tflatMap {
          case (partOf, availabilities, queryType, index) =>
            val params = MultipleWorksParams(
              page,
              pageSize,
              format,
              dateFrom,
              dateTo,
              languages,
              genre,
              subjects,
              contributors,
              // TODO: remove the plain "license" filter
              itemsLocationsLicense.orElse(license),
              includes,
              aggregations,
              sort,
              sortOrder,
              query,
              identifiers,
              itemLocationTypeId,
              accessStatus,
              workType,
              partOf,
              availabilities,
              queryType,
              index
            )
            validated(params.paginationErrors, params)
        }
    }

  implicit val formatFilter: Decoder[FormatFilter] =
    stringListFilter(FormatFilter)

  implicit val workTypeFilter: Decoder[WorkTypeFilter] =
    decodeOneOfCommaSeparated(
      "Collection" -> WorkType.Collection,
      "Series" -> WorkType.Series,
      "Section" -> WorkType.Section
    ).emap(values => Right(WorkTypeFilter(values)))

  implicit val itemLocationTypeIdFilter: Decoder[ItemLocationTypeIdFilter] =
    stringListFilter(ItemLocationTypeIdFilter)

  implicit val languagesFilter: Decoder[LanguagesFilter] =
    stringListFilter(LanguagesFilter)

  implicit val subjectFilter: Decoder[SubjectFilter] =
    stringListFilter(SubjectFilter)

  implicit val identifiersFilter: Decoder[IdentifiersFilter] =
    stringListFilter(IdentifiersFilter)

  implicit val partOf: Decoder[PartOfFilter] =
    Decoder.decodeString.map(PartOfFilter)

  implicit val availabilitiesFilter: Decoder[AvailabilitiesFilter] =
    stringListFilter(AvailabilitiesFilter)

  implicit val accessStatusFilter: Decoder[AccessStatusFilter] =
    decodeIncludesAndExcludes(
      "open" -> AccessStatus.Open,
      "open-with-advisory" -> AccessStatus.OpenWithAdvisory,
      "restricted" -> AccessStatus.Restricted,
      "closed" -> AccessStatus.Closed,
      "licensed-resources" -> AccessStatus.LicensedResources,
      "unavailable" -> AccessStatus.Unavailable,
      "permission-required" -> AccessStatus.PermissionRequired,
    ).emap {
      case (includes, excludes) => Right(AccessStatusFilter(includes, excludes))
    }

  implicit val aggregationsDecoder: Decoder[List[WorkAggregationRequest]] =
    decodeOneOfCommaSeparated(
      "workType" -> WorkAggregationRequest.Format,
      "genres" -> WorkAggregationRequest.GenreDeprecated,
      // TODO remove genres in favour of genres.label
      "genres.label" -> WorkAggregationRequest.Genre,
      "production.dates" -> WorkAggregationRequest.ProductionDate,
      // TODO remove subjects in favour of subjects.label
      "subjects" -> WorkAggregationRequest.SubjectDeprecated,
      "subjects.label" -> WorkAggregationRequest.Subject,
      "languages" -> WorkAggregationRequest.Languages,
      // TODO remove contributors in favour of contributors.agent.label
      "contributors" -> WorkAggregationRequest.ContributorDeprecated,
      "contributors.agent.label" -> WorkAggregationRequest.Contributor,
      // TODO remove license in favour of items.locations.license
      "license" -> WorkAggregationRequest.LicenseDeprecated,
      "items.locations.license" -> WorkAggregationRequest.License,
      "availabilities" -> WorkAggregationRequest.Availabilities
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
      "MultiMatcher" -> SearchQueryType.MultiMatcher,
    )
}
