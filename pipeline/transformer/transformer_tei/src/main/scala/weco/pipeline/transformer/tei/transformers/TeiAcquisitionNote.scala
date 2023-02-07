package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.Elem

/** Extract an acquisition[1] element as a Note, including the
  * att.datable.w3c[2] attributes expressed as prose in the Note text.
  *   1. https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-provenance.html,
  *      2.
  *      https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-att.datable.w3c.html
  */
object TeiAcquisitionNote extends Datable {
  def apply(acquisition: Elem): Option[Note] =
    NormaliseText(acquisition.text.trim).map(
      acquisitionText =>
        Note(
          noteType = NoteType.AcquisitionNote,
          contents = List(
            formatDatablePrefix(acquisition),
            Some(acquisitionText)
          ).flatten
            .mkString(": ")
        )
    )
}
