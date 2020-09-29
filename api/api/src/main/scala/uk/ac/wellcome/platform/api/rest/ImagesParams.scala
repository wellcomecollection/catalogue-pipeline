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
    ).emap(values => Right(SingleImageIncludes(values)))
}

case class MultipleImagesParams(
  page: Option[Int],
  pageSize: Option[Int],
  query: Option[String],
  license: Option[LicenseFilter],
  color: Option[ColorFilter],
  _index: Option[String]
) extends QueryParams
    with Paginated {

  def searchOptions(apiConfig: ApiConfig): SearchOptions =
    SearchOptions(
      searchQuery = query.map(SearchQuery(_)),
      filters = filters,
      pageSize = pageSize.getOrElse(apiConfig.defaultPageSize),
      pageNumber = page.getOrElse(1)
    )

  private def filters: List[ImageFilter] =
    List(
      license,
      color
    ).flatten
}

object MultipleImagesParams extends QueryParamsUtils {
  import CommonDecoders.licenseFilter

  def parse =
    parameter(
      (
        "page".as[Int].?,
        "pageSize".as[Int].?,
        "query".as[String].?,
        "locations.license".as[LicenseFilter].?,
        "color".as[ColorFilter].?,
        "_index".as[String].?
      )
    ).tflatMap { args =>
      val params = (MultipleImagesParams.apply _).tupled(args)
      validated(params.paginationErrors, params)
    }

  implicit val colorFilter: Decoder[ColorFilter] =
    decodeCommaSeparated.emap(strs => Right(ColorFilter(strs)))
}
