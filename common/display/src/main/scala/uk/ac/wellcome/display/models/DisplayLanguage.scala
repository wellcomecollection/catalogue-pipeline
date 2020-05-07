package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.Language

@Schema(
  name = "Language",
  description =
    "A language recognised as one of those in the ISO 639-2 language codes."
)
case class DisplayLanguage(
  @Schema(
    description = "An ISO 639-2 language code."
  ) id: Option[String],
  @Schema(
    description = "The name of a language"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Language"
)

case object DisplayLanguage {
  def apply(language: Language): DisplayLanguage = DisplayLanguage(
    id = language.id,
    label = language.label
  )
}
