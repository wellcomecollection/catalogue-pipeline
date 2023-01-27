package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work._

case class DisplayNote(
  contents: List[String],
  noteType: DisplayNoteType,
  @JsonKey("type") ontologyType: String = "Note"
)

case class DisplayNoteType(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "NoteType"
)

object DisplayNote {

  def apply(note: Note): DisplayNote =
    DisplayNote(
      contents = List(note.contents),
      noteType = DisplayNoteType(note)
    )

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
    DisplayNoteType(id = note.noteType.id, label = note.noteType.label)
}
