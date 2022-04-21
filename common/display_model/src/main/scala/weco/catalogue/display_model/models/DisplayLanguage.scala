package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.languages.Language

case class DisplayLanguage(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "Language"
)

case object DisplayLanguage {
  def apply(language: Language): DisplayLanguage = DisplayLanguage(
    id = language.id,
    label = language.label
  )
}
