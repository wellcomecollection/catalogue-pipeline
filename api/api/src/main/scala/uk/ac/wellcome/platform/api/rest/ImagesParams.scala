package uk.ac.wellcome.platform.api.rest

import io.circe.Decoder
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._

case class SingleImageParams(
  include: Option[SingleImageIncludes],
  _index: Option[String]
) extends QueryParams

object SingleImageParams extends QueryParamsUtils {
  def parse =
    parameter(
      (
        "include".as[SingleImageIncludes].?,
        "_index".as[String].?
      )
    ).tmap((SingleImageParams.apply _).tupled(_))

  implicit val includesDecoder: Decoder[SingleImageIncludes] =
    decodeOneOfCommaSeparated(
      "visuallySimilar" -> ImageInclude.VisuallySimilar,
      "withSimilarFeatures" -> ImageInclude.WithSimilarFeatures,
      "withSimilarColors" -> ImageInclude.WithSimilarColors,
      "source.contributors" -> ImageInclude.SourceContributors,
      "source.languages" -> ImageInclude.SourceLanguages,
    ).emap(values => Right(SingleImageIncludes(values: _*)))
}

case class MultipleImagesParams(
  page: Option[Int],
  pageSize: Option[Int],
  query: Option[String],
  license: Option[LicenseFilter],
  `source.contributors.agent.label`: Option[ContributorsFilter],
  color: Option[ColorMustQuery],
  include: Option[MultipleImagesIncludes],
  aggregations: Option[List[ImageAggregationRequest]],
  _index: Option[String]
) extends QueryParams
    with Paginated {

  def searchOptions(apiConfig: ApiConfig): ImageSearchOptions =
    ImageSearchOptions(
      searchQuery = query.map(SearchQuery(_)),
      filters = filters,
      mustQueries = mustQueries,
      aggregations = aggregations.getOrElse(Nil),
      pageSize = pageSize.getOrElse(apiConfig.defaultPageSize),
      pageNumber = page.getOrElse(1)
    )

  private def filters: List[ImageFilter] =
    List(license, `source.contributors.agent.label`).flatten

  private def mustQueries: List[ImageMustQuery] =
    List(color).flatten
}

object MultipleImagesParams extends QueryParamsUtils {
  import CommonDecoders._

  def parse =
    parameter(
      (
        "page".as[Int].?,
        "pageSize".as[Int].?,
        "query".as[String].?,
        "locations.license".as[LicenseFilter].?,
        "source.contributors.agent.label".as[ContributorsFilter].?,
        "color".as[ColorMustQuery].?,
        "include".as[MultipleImagesIncludes].?,
        "aggregations".as[List[ImageAggregationRequest]].?,
        "_index".as[String].?
      )
    ).tflatMap { args =>
      val params = (MultipleImagesParams.apply _).tupled(args)
      validated(params.paginationErrors, params)
    }

  implicit val colorMustQuery: Decoder[ColorMustQuery] =
    decodeCommaSeparated.emap(strs => Right(ColorMustQuery(strs)))

  implicit val includesDecoder: Decoder[MultipleImagesIncludes] =
    decodeOneOfCommaSeparated(
      "source.contributors" -> ImageInclude.SourceContributors,
      "source.languages" -> ImageInclude.SourceLanguages,
      "source.genres" -> ImageInclude.SourceGenres,
    ).emap(values => Right(MultipleImagesIncludes(values: _*)))

  implicit val aggregationsDecoder: Decoder[List[ImageAggregationRequest]] =
    decodeOneOfCommaSeparated(
      "locations.license" -> ImageAggregationRequest.License,
      "source.contributors.agent.label" -> ImageAggregationRequest.SourceContributors
    )
}
