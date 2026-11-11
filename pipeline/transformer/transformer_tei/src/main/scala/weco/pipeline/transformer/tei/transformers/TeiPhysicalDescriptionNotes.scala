package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import java.text.Normalizer
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
    val layoutDesc = objectDesc \ "layoutDesc"

    List(
      binding(physDesc \ "bindingDesc"),
      decoration(physDesc \ "decoDesc"),
      notes(supportDesc \ "condition", NoteType.ConditionNote),
      notes(layoutDesc \ "summary", NoteType.LayoutNote),
      notes(layoutDesc \ "p", NoteType.LayoutNote),
      layout(layoutDesc \ "layout"),
      notes(supportDesc \ "collation", NoteType.CollationNote),
      notes(supportDesc \ "foliation", NoteType.FoliationNote)
    ).flatten
  }

  def notes(nodes: NodeSeq, noteType: NoteType): List[Note] =
    nodes.flatMap(n => NormaliseText(textOf(n))).map(Note(noteType, _)).toList

  // Children that read as separate phrases, e.g. <locus>f. 2r</locus>Diagram
  private val separateElements = Set("locus", "note", "p")

  // Hand notes record letter measurements as segs, e.g. <seg type="density">
  private val measurementSegs =
    Set("density", "ratio", "anglealef", "anglekaf", "lines", "pace")

  /** Text of a node without bare measurements, which read as stray numbers,
    * e.g. "195 140 paper".
    */
  def textOf(node: Node): String =
    node match {
      case e: Elem if isMeasurement(e) => ""
      case e: Elem =>
        e.child
          .map(c => (textOf(c), separateElements(c.label)))
          .foldLeft(("", false)) {
            case ((acc, prevSeparate), (text, separate)) =>
              val needsSpace = (prevSeparate || separate) &&
                acc.lastOption.exists(c => !c.isWhitespace && c != '(') &&
                text.headOption.exists(c => c.isLetterOrDigit || c == '(')
              (acc + (if (needsSpace) " " else "") + text, separate)
          }
          ._1
      case a: Atom[_]   => a.text
      case e: EntityRef => e.text
      case _            => ""
    }

  private def isMeasurement(e: Elem): Boolean =
    e.label == "dimensions" ||
      (e.label == "measure" && !e.text.exists(_.isLetter)) ||
      (e.label == "seg" && measurementSegs((e \@ "type").trim))

  /** Whether text names a word, e.g. a script or a decoration type, ignoring
    * case, diacritics and transliteration marks, so "ta_liq" matches "taʿlīq".
    */
  def mentions(text: String, word: String): Boolean =
    s"(?U)\\b${Pattern.quote(fold(word))}\\b".r.findFirstIn(fold(text)).nonEmpty

  private def fold(s: String): String =
    Normalizer
      .normalize(s, Normalizer.Form.NFD)
      .replaceAll("[\\p{M}ʿʾ'_]", "")
      .toLowerCase

  /** An attribute value, ignoring placeholders left from the templates. */
  def attribute(node: Node, name: String): String =
    (node \@ name).trim match {
      case placeholder if placeholder.startsWith("<!--") => ""
      case value                                         => value
    }

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
            val decoType = attribute(decoNote, "type")
            val contents =
              if (
                decoType.isEmpty || decoType == "other" ||
                fold(text).startsWith(fold(decoType)) ||
                mentions(text, decoType)
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
        val writtenLines = attribute(layout, "writtenLines")
        // Only a leading locus applies to the whole layout
        val (locus, rest) =
          layout.child.dropWhile(_.text.trim.isEmpty).toList match {
            case first :: rest if first.label == "locus" =>
              (NormaliseText(textOf(first).stripSuffix(":")), rest)
            case children => (None, children)
          }
        val text = NormaliseText(
          textOf(<layout>{rest}</layout>)
        ).filterNot(_ == writtenLines)
        val counts = List(
          count(attribute(layout, "columns"), "column", text),
          count(writtenLines, "written line", text),
          count(attribute(layout, "ruledLines"), "ruled line", text)
        ).flatten
        NormaliseText((counts ++ text).mkString("; ")).map {
          description =>
            Note(
              NoteType.LayoutNote,
              locus.fold(description)(l => s"$l: $description")
            )
        }
    }

  // TEI allows a min and max pair, e.g. columns="1 2". The count is left out
  // when the text already gives it, e.g. "1 column, 18 lines".
  private def count(
    value: String,
    noun: String,
    text: Option[String]
  ): Option[String] = {
    val values = value.split("\\s+").filter(_.nonEmpty).toList
    val restated = values.nonEmpty && text.exists {
      t =>
        val number = values.map(Pattern.quote).mkString("\\s*(?:-|–|to)\\s*")
        val word = noun.split(" ").last
        s"(?iU)(?<![\\d.])$number\\s+(?:\\w+\\s+)?$word".r
          .findFirstIn(t)
          .nonEmpty
    }
    values match {
      case _ if restated => None
      case Nil           => None
      case List("1")     => Some(s"1 $noun")
      case _             => Some(s"${values.mkString(" to ")} ${noun}s")
    }
  }
}
