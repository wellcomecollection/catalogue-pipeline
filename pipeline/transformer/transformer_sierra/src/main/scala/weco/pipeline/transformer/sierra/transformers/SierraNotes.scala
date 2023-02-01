package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.text.TextNormalisation._
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.{Subfield, VarField}

import java.net.URL
import scala.util.Try
import scala.util.matching.Regex

object SierraNotes extends SierraDataTransformer with SierraQueryOps {

  type Output = List[Note]
  private val globallySuppressedSubfields = Set("5")

  val notesFields = Map(
    "500" -> createNoteFromContents(NoteType.GeneralNote),
    "501" -> createNoteFromContents(NoteType.GeneralNote),
    "502" -> createNoteFromContents(NoteType.DissertationNote),
    "504" -> createNoteFromContents(NoteType.BibliographicalInformation),
    "505" -> createNoteFromContents(NoteType.ContentsNote),
    "506" -> createNoteFromContents(NoteType.TermsOfUse),
    "508" -> createNoteFromContents(NoteType.CreditsNote),
    "510" -> createNoteFromContents(NoteType.ReferencesNote),
    "511" -> createNoteFromContents(NoteType.CreditsNote),
    "514" -> createNoteFromContents(NoteType.LetteringNote),
    "515" -> createNoteFromContents(NoteType.NumberingNote),
    "518" -> createNoteFromContents(NoteType.TimeAndPlaceNote),
    "524" -> createNoteFromContents(NoteType.CiteAsNote),
    "525" -> createNoteFromContents(NoteType.GeneralNote),
    "533" -> createNoteFromContents(NoteType.ReproductionNote),
    "534" -> createNoteFromContents(NoteType.ReproductionNote),
    "535" -> createLocationOfNote _,
    "536" -> createNoteFromContents(NoteType.FundingInformation),
    "540" -> createNoteFromContents(NoteType.TermsOfUse),
    "542" -> createNoteFromContents(NoteType.CopyrightNote),
    "544" -> createNoteFromContents(NoteType.RelatedMaterial),
    "545" -> createNoteFromContents(NoteType.BiographicalNote),
    "546" -> createNoteFromContents(NoteType.LanguageNote),
    "547" -> createNoteFromContents(NoteType.GeneralNote),
    "550" -> createNoteFromContents(NoteType.GeneralNote),
    "525" -> createNoteFromContents(NoteType.GeneralNote),
    "562" -> createNoteFromContents(NoteType.GeneralNote),
    "563" -> createNoteFromContents(NoteType.BindingInformation),
    "580" -> createNoteFromContents(NoteType.GeneralNote),
    "581" -> createNoteFromContents(NoteType.PublicationsNote),
    "585" -> createNoteFromContents(NoteType.ExhibitionsNote),
    "586" -> createNoteFromContents(NoteType.AwardsNote),
    "588" -> createNoteFromContents(NoteType.GeneralNote),
    // 591 subfield ǂ9 contains barcodes that we don't want to show on /works.
    "591" -> createNoteFromContents(
      NoteType.GeneralNote,
      suppressedSubfields = Set("9")),
    "593" -> createNoteFromContents(NoteType.CopyrightNote),
    "787" -> createNoteFrom787 _,
  )

  def apply(bibData: SierraBibData): List[Note] =
    bibData.varFields
      .map {
        // For the 561 "Ownership and Custodial History" field, we only want
        // to expose the note when the 1st indicator is 1 ("public note").
        // We don't want to expose the field otherwise.
        //
        // See https://www.loc.gov/marc/bibliographic/bd561.html
        case vf @ VarField(_, Some("561"), _, Some("1"), _, _) =>
          Some((vf, Some(createNoteFromContents(NoteType.OwnershipNote))))

        case vf @ VarField(_, Some(marcTag), _, _, _, _) =>
          Some((vf, notesFields.get(marcTag)))
        case _ => None
      }
      .collect {
        case Some((vf, Some(createNote))) => createNote(vf)
      }
      .filterNot { _.contents.isWhitespace }

  private def createNoteFromContents(
    noteType: NoteType,
    suppressedSubfields: Set[String] = Set()): VarField => Note =
    (varField: VarField) => {
      val contents =
        varField
          .subfieldsWithoutTags(
            (globallySuppressedSubfields ++ suppressedSubfields).toSeq: _*)
          .map {
            // We want to make ǂu into a clickable link, but only if it's a URL --
            // we don't want to make non-URLs into clickable objects.
            case Subfield("u", contents) if isUrl(contents.trim) =>
              s"""<a href="${contents.trim}">${contents.trim}</a>"""
            case Subfield("u", contents) =>
              warn(s"Subfield ǂu which doesn't look like a URL: $contents")
              contents

            case Subfield(_, contents) => contents
          }
          .mkString(" ")

      Note(contents = contents, noteType = noteType)
    }

  // In MARC 535, indicator 1 takes the following values:
  //
  //    1 = holder of originals
  //    2 = holder of duplicates
  //
  private def createLocationOfNote(vf: VarField): Note =
    vf.indicator1 match {
      case Some("2") =>
        createNoteFromContents(NoteType.LocationOfDuplicatesNote)(vf)
      case _ => createNoteFromContents(NoteType.LocationOfOriginalNote)(vf)
    }

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
  private def createNoteFrom787(vf: VarField): Note = {
    val contents =
      vf.subfieldsWithoutTags(globallySuppressedSubfields.toSeq: _*)
        .map {
          case Subfield("w", contents) =>
            contents match {
              case uklwPrefixRegex(bibNumber) =>
                s"""(<a href="https://wellcomecollection.org/search/works?query=${bibNumber.trim}">${bibNumber.trim}</a>)"""

              case _ => contents
            }

          case Subfield(_, contents) => contents
        }
        .mkString(" ")

    Note(contents = contents, noteType = NoteType.RelatedMaterial)
  }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess

}
