package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.locations.License

case class DisplayLicense(
  id: String,
  label: String,
  url: String,
  @JsonKey("type") ontologyType: String = "License"
)

case object DisplayLicense {
  def apply(license: License): DisplayLicense = DisplayLicense(
    id = license.id,
    label = license.label,
    url = license.url
  )
}
