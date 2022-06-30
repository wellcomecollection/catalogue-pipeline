package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.Elem

/**
  * Extract a provenance[1] element as a Note, including the att.datable.w3c[2] attributes
  * expressed as prose in the Note text.
  * 1. https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-provenance.html,
  * 2. https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-att.datable.w3c.html
  */
object TeiProvenanceNote extends Datable {
  def apply(provenance: Elem): Option[Note] =
    NormaliseText(provenance.text.trim).map(
      provenanceText =>
        Note(
          NoteType.OwnershipNote,
          List(formatDatablePrefix(provenance), Some(provenanceText)).flatten
            .mkString(": ")
      ))
}
