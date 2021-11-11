package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, NodeSeq}

object TeiNotes {
  def apply(xml: Elem): List[Note] =
    apply(xml \\ "msDesc" \ "msContents")

  def apply(node: NodeSeq): List[Note] =
    getColophon(node).toList

  /** The colophon is in `colophon` nodes under `msContents` or `msItem`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
    *   <teiHeader>
    *     <fileDesc>
    *       <sourceDesc>
    *         <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *           <msContents>
    *             <colophon> <locus>F. 9v</locus> </colophon>
    *
    */
  private def getColophon(value: NodeSeq): Seq[Note] =
    (value \ "colophon")
      .map { n =>
        n.text.trim
      }
      .flatMap { NormaliseText(_) }
      .filter { _.nonEmpty }
      .map { contents =>
        Note(contents = contents, noteType = NoteType.ColophonNote)
      }
}
