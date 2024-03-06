package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}
import weco.pipeline.transformer.marc_common.transformers.MarcNotes
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.fields.SierraMaterialType

import scala.util.matching.Regex

object SierraNotes
    extends MarcNotes
    with SierraDataTransformer
    with SierraMarcDataConversions {

  type Output = Seq[Note]

  // This regex matches any string starting with (UkLW), followed by
  // any number of spaces, and then captures everything after the
  // space, which is the bib number we're interested in.
  //
  // The UkLW match is case insensitive because there are sufficient
  // inconsistencies in the source data that it's easier to handle that here.
  private val uklwPrefixRegex: Regex = """\((?i:UkLW)\)[\s]*(.+)""".r.anchored

  // In MARC 787, subfield $w may contain a catalogue reference, in which
  // case we want to create a clickable link.
  //
  // Eventually it'd be nice if these went direct to the works page, but for
  // now a canned search is enough.
  private def createNoteFrom787(field: MarcField): Note = {
    val contents =
      subfieldsWithoutTags(field, globallySuppressedSubfields.toSeq)
        .map {
          case MarcSubfield("w", contents) =>
            contents match {
              case uklwPrefixRegex(bibNumber) =>
                s"""(<a href="https://wellcomecollection.org/search/works?query=${bibNumber.trim}">${bibNumber.trim}</a>)"""

              case _ => contents
            }

          case MarcSubfield(_, contents) => contents
        }
        .mkString(" ")

    Note(contents = contents, noteType = NoteType.RelatedMaterial)
  }

  override val notesFields: Map[String, MarcField => Note] =
    MarcNotes.notesFields + (
      // The 787 -> search by bibNumber is a Sierra-specific Note behaviour
      "787" -> createNoteFrom787,
      // 59x is the Local Notes space, so these are inherently Sierra-specific
      // https://www.loc.gov/marc/bibliographic/bd59x.html
      // 591 subfield ǂ9 contains barcodes that we don't want to show on /works.
      "591" -> createNoteFromContents(
        NoteType.GeneralNote,
        suppressedSubfields = Set("9")
      ),
      "593" -> createNoteFromContents(NoteType.CopyrightNote)
    )

  def apply(bibData: SierraBibData): Seq[Note] = {
    val transformer514 = bibData.materialType match {
      case Some(SierraMaterialType("k")) =>
        (_: MarcField) => Note(NoteType.LetteringNote, "")
      case _ => createNoteFromContents(NoteType.LetteringNote)
    }
    toNotes(notesFields + ("514" -> transformer514))(
      bibDataToMarcRecord(bibData)
    )
  }
}
