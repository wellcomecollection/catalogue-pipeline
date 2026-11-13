package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Comment, Elem, Node, NodeSeq}

/** The bibliography of a manuscript, part or item: one note per <bibl>.
  *
  * nodeSeq is an msDesc or msPart (bibliography in additional/listBibl) or an
  * msItem (bibliography in listBibl).
  */
object TeiBibliography {
  def apply(nodeSeq: NodeSeq): List[Note] =
    ((nodeSeq \ "additional" \ "listBibl" \ "bibl") ++
      (nodeSeq \ "listBibl" \ "bibl")).toList
      .flatMap(reference)
      .map(Note(NoteType.BibliographicalInformation, _))

  private val citationFields = Set(
    "abbr",
    "author",
    "editor",
    "title",
    "placeName",
    "pubPlace",
    "publisher",
    "date",
    "biblScope",
    "note"
  )

  // A <bibl> written as fields has no punctuation between them, so the
  // citation is put together here; anything else keeps its own text.
  private def reference(bibl: Node): Option[String] =
    if (isWrittenAsFields(bibl)) citation(bibl)
    else NormaliseText(TeiPhysicalDescriptionNotes.textOf(bibl))

  private def isWrittenAsFields(bibl: Node): Boolean = {
    val fields = bibl.child.collect { case e: Elem => e.label }
    fields.exists(Set("author", "editor", "title", "publisher")) &&
    fields.forall(citationFields) &&
    bibl.child.forall {
      case _: Elem | _: Comment => true
      case n                    => n.text.trim.isEmpty
    }
  }

  // e.g. "NCC: A. Editor (ed.), Title (Place: Publisher, 1949), pp. 1-2"
  private def citation(bibl: Node): Option[String] = {
    def values(label: String): List[String] =
      (bibl \ label).toList.flatMap(
        n => NormaliseText(TeiPhysicalDescriptionNotes.textOf(n))
      )
    def joined(labels: String*): Option[String] =
      Some(labels.flatMap(values).mkString(", ")).filter(_.nonEmpty)

    val names = (values("author"), values("editor")) match {
      case (Nil, Nil)     => None
      case (Nil, editors) => Some(s"${editors.mkString(" and ")} (ed.)")
      case (authors, _)   => Some(authors.mkString(" and "))
    }
    val publication =
      List(joined("placeName", "pubPlace"), joined("publisher")).flatten
        .mkString(": ")
    val imprint =
      List(Some(publication).filter(_.nonEmpty), joined("date")).flatten
        .mkString(", ")
    val work = List(names, joined("title")).flatten.mkString(", ") +
      (if (imprint.isEmpty) "" else s" ($imprint)")
    val reference = (Some(work.trim).filter(_.nonEmpty).toList ++
      joined("biblScope", "note")).mkString(", ")

    NormaliseText(
      (joined("abbr"), reference) match {
        case (Some(abbr), "") => abbr
        case (Some(abbr), _)  => s"$abbr: $reference"
        case (None, _)        => reference
      }
    )
  }
}
