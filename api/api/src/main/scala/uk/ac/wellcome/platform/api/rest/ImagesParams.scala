package uk.ac.wellcome.platform.api.rest

case class SingleImageParams(
  _index: Option[String]
) extends QueryParams

object SingleImageParams extends QueryParamsUtils {
  def parse =
    parameter("_index".as[String].?)
      .map(SingleImageParams.apply)
}
