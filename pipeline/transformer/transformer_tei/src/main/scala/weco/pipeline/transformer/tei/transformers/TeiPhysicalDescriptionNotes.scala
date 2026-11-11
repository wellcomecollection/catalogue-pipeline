package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{NodeSeq, Text}

/** Parts of physDesc that get their own notes rather than joining the physical
  * description, so each can be shown under its own heading.
  *
  * nodeSeq is an msDesc (for the wrapper work) or an msPart.
  */
object TeiPhysicalDescriptionNotes {
  def apply(nodeSeq: NodeSeq): List[Note] = {
    val physDesc = nodeSeq \ "physDesc"
    val supportDesc = physDesc \ "objectDesc" \ "supportDesc"

    List(
      notes(physDesc \ "bindingDesc", NoteType.BindingInformation),
      decoration(physDesc \ "decoDesc" \ "decoNote"),
      notes(supportDesc \ "condition", NoteType.ConditionNote),
      layout(physDesc \ "objectDesc" \ "layoutDesc" \ "layout"),
      notes(supportDesc \ "collation", NoteType.CollationNote),
      notes(supportDesc \ "foliation", NoteType.FoliationNote)
    ).flatten
  }

  private def notes(nodes: NodeSeq, noteType: NoteType): List[Note] =
    nodes.flatMap(n => NormaliseText(n.text)).map(Note(noteType, _)).toList

  /** <decoNote type="rubrication">Rubrication throughout.</decoNote>
    *
    * The templates preset a type and leave the text empty, so a decoNote with
    * no text is a leftover and is skipped.
    */
  private def decoration(decoNotes: NodeSeq): List[Note] =
    decoNotes.flatMap {
      decoNote =>
        NormaliseText(decoNote.text).map {
          text =>
            val decoType = (decoNote \@ "type").trim
            val contents =
              if (
                decoType.isEmpty || decoType == "other" || text.toLowerCase
                  .startsWith(decoType.toLowerCase)
              ) text
              else s"${decoType.capitalize}: $text"
            Note(NoteType.DecorationNote, contents)
        }
    }.toList

  /** <layout columns="1" writtenLines="21">21</layout>
    *
    * Only the layout's own text is kept: child elements such as line-height
    * dimensions would read as stray numbers. Text that only repeats
    * writtenLines is dropped.
    */
  private def layout(layouts: NodeSeq): List[Note] =
    layouts.flatMap {
      layout =>
        val columns = (layout \@ "columns").trim
        val writtenLines = (layout \@ "writtenLines").trim
        val text = layout.child.collect { case t: Text => t.text }.mkString
        val parts = List(
          plural(columns, "column"),
          plural(writtenLines, "written line"),
          NormaliseText(text).filterNot(_ == writtenLines)
        ).flatten
        NormaliseText(parts.mkString("; ")).map(Note(NoteType.LayoutNote, _))
    }.toList

  private def plural(count: String, noun: String): Option[String] =
    count match {
      case ""  => None
      case "1" => Some(s"1 $noun")
      case n   => Some(s"$n ${noun}s")
    }
}
