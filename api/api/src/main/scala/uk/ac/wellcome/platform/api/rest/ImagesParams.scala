package uk.ac.wellcome.platform.api.rest

import uk.ac.wellcome.platform.api.models.LicenseFilter

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
  license: Option[LicenseFilter]
) extends QueryParams
    with Paginated

object MultipleImagesParams extends QueryParamsUtils {
  import CommonDecoders.licenseFilter

  def parse =
    parameter(
      (
        "page".as[Int].?,
        "pageSize".as[Int].?,
        "query".as[String].?,
        "license".as[LicenseFilter].?,
      )
    ).tflatMap { args =>
      val params = (MultipleImagesParams.apply _).tupled(args)
      validated(params.paginationErrors, params)
    }
}
