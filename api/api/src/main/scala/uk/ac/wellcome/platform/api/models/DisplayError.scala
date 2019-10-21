package uk.ac.wellcome.platform.api.models

import io.swagger.v3.oas.annotations.media.Schema
import io.circe.generic.extras.JsonKey
import io.circe.Encoder
import io.circe.generic.extras.semiauto._
import uk.ac.wellcome.display.json.DisplayJsonUtil._

@Schema(
  name = "Error"
)
case class DisplayError(
  @Schema(
    description = "The type of error",
    allowableValues = Array("http")
  ) errorType: String,
  @Schema(
    `type` = "Int",
    description = "The HTTP response status code"
  ) httpStatus: Option[Int] = None,
  @Schema(
    description = "The title or other short name of the error"
  ) label: String,
  @Schema(
    `type` = "String",
    description = "The specific error"
  ) description: Option[String] = None,
  @JsonKey("type") ontologyType: String = "Error"
)

object DisplayError {

  implicit val encoder: Encoder[DisplayError] = deriveEncoder

  def apply(error: Error): DisplayError = DisplayError(
    errorType = error.errorType,
    httpStatus = error.httpStatus,
    label = error.label,
    description = error.description
  )

  def apply(variant: ErrorVariant, description: Option[String]): DisplayError =
    DisplayError(Error(variant, description))
}
