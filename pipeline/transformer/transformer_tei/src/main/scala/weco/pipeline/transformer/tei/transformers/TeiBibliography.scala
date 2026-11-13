package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Comment, Elem, Node, NodeSeq}

/** The bibliography of a manuscript, part or item: one note per <bibl>.
  *
  * nodeSeq is an msDesc or msPart (bibliography in additional/listBibl) or an
  * msItem (bibliography in listBibl, or a bibl directly under it).
  */
object TeiBibliography {
  def apply(nodeSeq: NodeSeq): List[Note] =
    ((nodeSeq \ "additional" \ "listBibl").flatMap(bibls) ++
      (nodeSeq \ "listBibl").flatMap(bibls) ++ (nodeSeq \ "bibl")).toList
      .flatMap(reference)
      .map(Note(NoteType.BibliographicalInformation, _))

  // The TEI template allows a listBibl to hold further listBibl elements
  private def bibls(listBibl: Node): Seq[Node] =
    listBibl.child.flatMap {
      case n if n.label == "bibl"     => Seq(n)
      case n if n.label == "listBibl" => bibls(n)
      case _                          => Nil
    }

  private val citationFields = List(
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

  private def isWrittenAsFields(bibl: Node): Boolean =
    bibl.child.exists(
      n => Set("author", "editor", "title", "publisher")(n.label)
    ) &&
      bibl.child.forall {
        case _: Elem | _: Comment => true
        case n                    => n.text.trim.isEmpty
      }

  // e.g. "NCC: A. Editor (ed.), Title (Place: Publisher, 1949), pp. 1-2"
  private def citation(bibl: Node): Option[String] = {
    def text(n: Node): Option[String] =
      NormaliseText(TeiPhysicalDescriptionNotes.textOf(n))
    def values(label: String): List[String] =
      (bibl \ label).toList.flatMap(text)
    def joined(labels: String*): Option[String] =
      Some(labels.flatMap(values).mkString(", ")).filter(_.nonEmpty)

    val names = (values("author") match {
      case Nil     => Nil
      case authors => List(authors.mkString(" and "))
    }) ++ (values("editor") match {
      case Nil     => Nil
      case editors => List(s"${editors.mkString(" and ")} (ed.)")
    })
    val nameAndTitle = (names ++ joined("title")).mkString(", ")
    val imprint = List(
      Some(
        List(joined("placeName", "pubPlace"), joined("publisher")).flatten
          .mkString(": ")
      ).filter(_.nonEmpty),
      joined("date")
    ).flatten.mkString(", ")
    val work = (nameAndTitle, imprint) match {
      case ("", _) => imprint
      case (_, "") => nameAndTitle
      case _       => s"$nameAndTitle ($imprint)"
    }
    // Fields we don't name, e.g. <ref> or <extent>, go at the end
    val others = bibl.child
      .collect {
        case e: Elem if !citationFields.contains(e.label) => e
      }
      .flatMap(text)
    val details =
      (Some(work).filter(_.nonEmpty).toList ++ joined("biblScope", "note") ++
        others).mkString(", ")

    (joined("abbr"), details) match {
      case (Some(abbr), "") => Some(abbr)
      case (Some(abbr), _)  => Some(s"$abbr: $details")
      case (None, "")       => None
      case (None, _)        => Some(details)
    }
  }
}
