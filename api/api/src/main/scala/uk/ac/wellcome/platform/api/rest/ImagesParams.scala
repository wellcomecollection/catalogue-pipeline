package uk.ac.wellcome.platform.api.rest

import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.services.ImagesSearchOptions

case class SingleImageParams(
  _index: Option[String]
) extends QueryParams

object SingleImageParams extends QueryParamsUtils {
  def parse =
    parameter("_index".as[String].?)
      .map(SingleImageParams.apply)
}

case class MultipleImagesParams(
  page: Option[Int],
  pageSize: Option[Int],
  query: Option[String],
  license: Option[LicenseFilter],
  _index: Option[String]
) extends QueryParams
    with Paginated {

  def searchOptions(apiConfig: ApiConfig): ImagesSearchOptions =
    ImagesSearchOptions(
      searchQuery = query.map(SearchQuery(_)),
      filters = filters,
      pageSize = pageSize.getOrElse(apiConfig.defaultPageSize),
      pageNumber = page.getOrElse(1)
    )

  private def filters: List[ImageFilter] =
    license.toList
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
        "_index".as[String].?
      )
    ).tflatMap { args =>
      val params = (MultipleImagesParams.apply _).tupled(args)
      validated(params.paginationErrors, params)
    }
}
