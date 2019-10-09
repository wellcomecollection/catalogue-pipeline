package uk.ac.wellcome.display.models

import com.fasterxml.jackson.annotation.JsonProperty
import io.circe.generic.extras.JsonKey
import io.swagger.annotations.{ApiModel, ApiModelProperty}

import uk.ac.wellcome.models.work.internal._

@ApiModel(
  value = "Note",
  description = "A note associated with the work."
)
case class DisplayNote(
  @ApiModelProperty(value = "The note contents.") contents: List[String],
  @ApiModelProperty(value = "The type of note.") noteType: DisplayNoteType,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Note"
)

case class DisplayNoteType(
  @ApiModelProperty(
    dataType = "String"
  ) id: String,
  @ApiModelProperty(
    dataType = "String"
  ) label: String,
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "NoteType"
)

object DisplayNote {

  def apply(note: Note): DisplayNote =
    DisplayNote(List(note.content), DisplayNoteType(note))

  def merge(notes: List[DisplayNote]): List[DisplayNote] =
    notes
      .groupBy(_.noteType)
      .toList
      .map { case (noteType, notes) =>
        DisplayNote(notes.flatMap(_.contents), noteType)
      }
}

object DisplayNoteType {

  def apply(note: Note): DisplayNoteType =
    note match {
      case GeneralNote(_) =>
        DisplayNoteType("general-note", "General note")
      case BibliographicalInformation(_) =>
        DisplayNoteType("bibliographical-info", "Bibliographical information")
      case FundingInformation(_) =>
        DisplayNoteType("funding-info", "Funding information")
      case TimeAndPlaceNote(_) =>
        DisplayNoteType("time-and-place-note", "Time and place note")
      case CreditsNote(_) =>
        DisplayNoteType("credits-note", "Credits note")
      case ContentsNote(_) =>
        DisplayNoteType("contents-note", "Contents note")
    }
}
