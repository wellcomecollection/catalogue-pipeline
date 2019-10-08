package uk.ac.wellcome.display.models

import com.fasterxml.jackson.annotation.JsonProperty
import io.circe.generic.extras.JsonKey
import io.swagger.annotations.{ApiModel, ApiModelProperty}

import uk.ac.wellcome.models.work.internal.Note

@ApiModel(
  value = "Note",
  description = "A note associated with the work."
)
case class DisplayNote(
  @ApiModelProperty(value = "The note content.") content: String,
  @JsonProperty("type") @JsonKey("type") noteType: String
)

object DisplayNote {

  def apply(note: Note): DisplayNote =
    DisplayNote(note.content, note.getClass.getSimpleName)
}
