package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.License

@Schema(
  name = "License",
  description =
    "The specific license under which the work in question is released to the public - for example, one of the forms of Creative Commons - if it is a precise license to which a link can be made."
)
case class DisplayLicenseV2(
  @Schema(
    description =
      "A type of license under which the work in question is released to the public.",
    allowableValues = Array("cc-by", "cc-by-nc", "cc-by-nc-nd", "cc-0, pdm")
  ) id: String,
  @Schema(
    description = "The title or other short name of a license"
  ) label: String,
  @Schema(
    description = "URL to the full text of a license"
  ) url: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "License"
)

case object DisplayLicenseV2 {
  def apply(license: License): DisplayLicenseV2 = DisplayLicenseV2(
    id = license.id,
    label = license.label,
    url = license.url
  )
}
