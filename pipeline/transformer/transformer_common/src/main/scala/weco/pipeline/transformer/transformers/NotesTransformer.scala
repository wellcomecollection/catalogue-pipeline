package weco.pipeline.transformer.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}

trait NotesTransformer {
  def createLocationOfDuplicatesNote(text: String): Option[Note] = {
    // If the note only talks about digitisation, we don't want to show it on the page.
    // Either
    Some(Note(contents = text, noteType = NoteType.LocationOfDuplicatesNote))
  }
}
