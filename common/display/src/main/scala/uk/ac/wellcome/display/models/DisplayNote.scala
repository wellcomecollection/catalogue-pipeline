package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema

import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Note",
  description = "A note associated with the work."
)
case class DisplayNote(
  @Schema(description = "The note contents.") contents: List[String],
  @Schema(description = "The type of note.") noteType: DisplayNoteType,
  @JsonKey("type") ontologyType: String = "Note"
)

@Schema(
  name = "NoteType",
  description = "Indicates the type of note associated with the work."
)
case class DisplayNoteType(
  @Schema(
    `type` = "String"
  ) id: String,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") ontologyType: String = "NoteType"
)

object DisplayNote {

  def apply(note: Note): DisplayNote =
    DisplayNote(List(note.content), DisplayNoteType(note))

  def merge(notes: List[DisplayNote]): List[DisplayNote] =
    notes
      .groupBy(_.noteType)
      .toList
      .map {
        case (noteType, notes) =>
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
      case CiteAsNote(_) =>
        DisplayNoteType("cite-as-note", "Cite-as note")
      case DissertationNote(_) =>
        DisplayNoteType("dissertation-note", "Dissertation note")
      case LocationOfOriginalNote(_) =>
        DisplayNoteType(
          "location-of-original-note",
          "Location of original note")
      case BindingInformation(_) =>
        DisplayNoteType("binding-information", "Binding information")
    }
}
