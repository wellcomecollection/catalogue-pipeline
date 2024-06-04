package weco.pipeline.transformer.marc_common.transformers
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcRecord,
  MarcSubfield
}
import weco.pipeline.transformer.text.TextNormalisation._

import java.net.URL
import scala.util.Try
trait MarcNotes extends Logging {

  protected val globallySuppressedSubfields: Set[String] = Set("5")
  private val codebreakersLocationSentences: List[String] = List(
    "This catalogue is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics.",
    "A digitised copy is held by the Wellcome Library as part of the Codebreakers: Makers of Modern Genetics programme.",
    "A digitised copy is held by Wellcome Collection as part of Codebreakers: Makers of Modern Genetics.",
    "This catalogue is held by the Wellcome Library as part of the Codebreakers: Makers of Modern Genetics programme.",
    "A digitised copy is held by the Wellcome Library as part of Codebreakers: Makers of Modern Genetics."
  )

  val notesFields: Map[String, MarcField => Note] = Map(
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
    "535" -> createLocationOfNote,
    "536" -> createNoteFromContents(NoteType.FundingInformation),
    "540" -> createNoteFromContents(NoteType.TermsOfUse),
    "542" -> createNoteFromContents(NoteType.CopyrightNote),
    "544" -> createNoteFromContents(NoteType.RelatedMaterial),
    "545" -> createNoteFromContents(NoteType.BiographicalNote),
    "546" -> createNoteFromContents(NoteType.LanguageNote),
    "547" -> createNoteFromContents(NoteType.GeneralNote),
    "550" -> createNoteFromContents(NoteType.GeneralNote),
    "525" -> createNoteFromContents(NoteType.GeneralNote),
    "561" -> createOwnershipNote,
    "562" -> createNoteFromContents(NoteType.GeneralNote),
    "563" -> createNoteFromContents(NoteType.BindingInformation),
    "580" -> createNoteFromContents(NoteType.GeneralNote),
    "581" -> createNoteFromContents(NoteType.PublicationsNote),
    "585" -> createNoteFromContents(NoteType.ExhibitionsNote),
    "586" -> createNoteFromContents(NoteType.AwardsNote),
    "588" -> createNoteFromContents(NoteType.GeneralNote)
  )
  def toNotes(
    noteTransformers: Map[String, MarcField => Note]
  )(marcRecord: MarcRecord): Seq[Note] =
    marcRecord.fields map {
      field =>
        noteTransformers.get(field.marcTag).map(_(field))
    } collect {
      case Some(note) if !note.contents.isWhitespace => note
    }

  protected def subfieldsWithoutTags(
    marcField: MarcField,
    tags: Seq[String]
  ): Seq[MarcSubfield] =
    marcField.subfields.filterNot(subfield => tags.contains(subfield.tag))

  /** Conditionally create an Ownership note, depending on the privacy of the
    * original note.
    *
    * A note is only created if it is explicitly marked as "Not Private" by
    * setting first indicator to 1
    *
    * https://www.loc.gov/marc/bibliographic/bd561.html
    */
  private def createOwnershipNote(marcField: MarcField): Note = {

    if (marcField.indicator1 == "1")
      createNoteFromContents(NoteType.OwnershipNote)(marcField)
    else
      // I'd prefer it to return None, but that would require a bit of change elsewhere
      Note(NoteType.OwnershipNote, "")
  }
  protected def createNoteFromContents(
    noteType: NoteType,
    suppressedSubfields: Set[String] = Set()
  ): MarcField => Note =
    (marcField: MarcField) => {
      val contents =
        subfieldsWithoutTags(
          marcField,
          (globallySuppressedSubfields ++ suppressedSubfields).toSeq
        )
          .map {
            // We want to make ǂu into a clickable link, but only if it's a URL --
            // we don't want to make non-URLs into clickable objects.
            case MarcSubfield("u", contents) if isUrl(contents.trim) =>
              s"""<a href="${contents.trim}">${contents.trim}</a>"""
            case MarcSubfield("u", contents) =>
              warn(s"Subfield ǂu which doesn't look like a URL: $contents")
              contents

            case MarcSubfield(_, contents) => contents
          }
          .mkString(" ")

      // We want to remove all sentences mentioning Codebreakers from the location note.
      // This involves filtering out 5 distinct sentences, which are hardcoded in `codebreakersLocationSentences`.
      // Note that we don't want to get rid of the whole field. It might have useful information in other sentences.
      val contentsWithoutCodebreakerReferences = codebreakersLocationSentences
        .foldLeft(contents)(
          (currentContents, codebreakersSentence) => {
            // Match an optional leading white space to make sure that removing a sentence doesn't lead to double spaces
            val regex = ("\\s?" + codebreakersSentence).r
            regex.replaceAllIn(currentContents, "")
          }
        )
        .trim()

      Note(contents = contentsWithoutCodebreakerReferences, noteType = noteType)
    }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess

  // In MARC 535, indicator 1 takes the following values:
  //
  //    1 = holder of originals
  //    2 = holder of duplicates
  //
  private def createLocationOfNote(field: MarcField): Note =
    field.indicator1 match {
      case "2" =>
        createNoteFromContents(NoteType.LocationOfDuplicatesNote)(field)
      case _ => createNoteFromContents(NoteType.LocationOfOriginalNote)(field)
    }

}

object MarcNotes extends MarcNotes with MarcDataTransformer {
  type Output = Seq[Note]
  def apply(marcRecord: MarcRecord): Seq[Note] =
    toNotes(notesFields)(marcRecord)
}
