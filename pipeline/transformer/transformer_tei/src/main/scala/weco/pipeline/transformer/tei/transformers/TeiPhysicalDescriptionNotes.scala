package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import java.util.regex.Pattern
import scala.xml.{Atom, Elem, EntityRef, Node, NodeSeq}

/** Parts of physDesc that get their own notes rather than joining the physical
  * description, so each can be shown under its own heading.
  *
  * nodeSeq is an msDesc (for the wrapper work) or an msPart.
  */
object TeiPhysicalDescriptionNotes {
  def apply(nodeSeq: NodeSeq): List[Note] = {
    val physDesc = nodeSeq \ "physDesc"
    val objectDesc = physDesc \ "objectDesc"
    val supportDesc = objectDesc \ "supportDesc"

    List(
      binding(physDesc \ "bindingDesc"),
      decoration(physDesc \ "decoDesc"),
      notes(supportDesc \ "condition", NoteType.ConditionNote),
      layout(objectDesc \ "layoutDesc" \ "layout"),
      notes(supportDesc \ "collation", NoteType.CollationNote),
      notes(supportDesc \ "foliation", NoteType.FoliationNote)
    ).flatten
  }

  def notes(nodes: NodeSeq, noteType: NoteType): List[Note] =
    nodes.flatMap(n => NormaliseText(textOf(n))).map(Note(noteType, _)).toList

  // Bare measurements read as stray numbers in a note, e.g. "195 140 paper"
  private def textOf(node: Node): String =
    node match {
      case e: Elem if e.label == "dimensions" || e.label == "measure" => ""
      case e: Elem      => e.child.map(textOf).mkString
      case a: Atom[_]   => a.text
      case e: EntityRef => e.text
      case _            => ""
    }

  /** Whether text names a word, e.g. a script or a line count. */
  def mentions(text: String, word: String): Boolean =
    s"(?iU)\\b${Pattern.quote(word)}\\b".r.findFirstIn(text).nonEmpty

  private def binding(bindingDescs: NodeSeq): List[Note] =
    bindingDescs.toList.flatMap {
      bindingDesc =>
        val bindings = bindingDesc \ "binding"
        notes(
          if (bindings.isEmpty) bindingDesc else bindings,
          NoteType.BindingInformation
        )
    }

  // The templates preset a decoNote type with no text, so those are skipped
  private def decoration(decoDescs: NodeSeq): List[Note] = {
    val decoNotes = (decoDescs \ "decoNote").flatMap {
      decoNote =>
        NormaliseText(textOf(decoNote)).map {
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
    }
    notes(decoDescs \ "summary", NoteType.DecorationNote) ++
      notes(decoDescs \ "p", NoteType.DecorationNote) ++ decoNotes
  }

  private def layout(layouts: NodeSeq): List[Note] =
    layouts.toList.flatMap {
      layout =>
        val writtenLines = (layout \@ "writtenLines").trim
        val locus = NormaliseText((layout \ "locus").text)
        val text = NormaliseText(
          layout.child.filterNot(_.label == "locus").map(textOf).mkString
        ).filterNot(_ == writtenLines)
        // The text often restates the count, e.g. "30 written lines"
        val writtenLinesCount =
          if (
            writtenLines
              .split("\\s+")
              .headOption
              .exists(n => text.exists(mentions(_, n)))
          ) None
          else count(writtenLines, "written line")
        val counts = List(
          count(layout \@ "columns", "column"),
          writtenLinesCount,
          count(layout \@ "ruledLines", "ruled line")
        ).flatten
        NormaliseText((counts ++ text).mkString("; ")).map {
          description =>
            Note(
              NoteType.LayoutNote,
              locus.fold(description)(l => s"$l: $description")
            )
        }
    }

  // TEI allows a min and max pair, e.g. columns="1 2"
  private def count(value: String, noun: String): Option[String] =
    value.trim.split("\\s+").filter(_.nonEmpty).toList match {
      case Nil       => None
      case List("1") => Some(s"1 $noun")
      case values    => Some(s"${values.mkString(" to ")} ${noun}s")
    }
}
