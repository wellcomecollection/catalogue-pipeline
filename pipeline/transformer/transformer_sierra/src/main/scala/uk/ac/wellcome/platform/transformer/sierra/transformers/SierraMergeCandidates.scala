package uk.ac.wellcome.platform.transformer.sierra.transformers

import java.util.UUID

import uk.ac.wellcome.models.work.internal.{
  IdentifierType,
  MergeCandidate,
  SourceIdentifier
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}
import uk.ac.wellcome.platform.transformer.sierra.transformers.parsers.{
  MiroIdParser,
  WellcomeImagesURLParser
}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

import scala.util.Try
import scala.util.matching.Regex

object SierraMergeCandidates
    extends SierraTransformer
    with SierraQueryOps
    with WellcomeImagesURLParser
    with MiroIdParser {

  type Output = List[MergeCandidate]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    get776mergeCandidates(bibData) ++
      getSinglePageMiroMergeCandidates(bibData) ++ get035CalmMergeCandidates(
      bibData)

  // This regex matches any string starting with (UkLW), followed by
  // any number of spaces, and then captures everything after the
  // space, which is the bib number we're interested in.
  private val uklwPrefixRegex: Regex = """\(UkLW\)[\s]*(.+)""".r.anchored

  /** We can merge a bib and the digitised version of that bib.  The number
    * of the other bib comes from MARC tag 776 subfield $w.
    *
    * If the identifier starts with (UkLW), we strip the prefix and use the
    * bib number as a merge candidate.
    *
    */
  private def get776mergeCandidates(
    bibData: SierraBibData): List[MergeCandidate] =
    bibData
      .subfieldsWithTag("776" -> "w")
      .contents
      .map {
        case uklwPrefixRegex(bibNumber) => Some(bibNumber)
        case _                          => None
      }
      .distinct match {
      case List(Some(bibNumber)) =>
        List(
          MergeCandidate(
            identifier = SourceIdentifier(
              identifierType = IdentifierType("sierra-system-number"),
              ontologyType = "Work",
              value = bibNumber
            ),
            reason = Some("Physical/digitised Sierra work")
          )
        )
      case _ => Nil
    }

  /*
   * We always try to merge all linked Miro and Sierra works
   */
  private def getSinglePageMiroMergeCandidates(
    bibData: SierraBibData): List[MergeCandidate] =
    (matching089Ids(bibData) ++ matching962Ids(bibData)).distinct
      .map {
        miroMergeCandidate(_, "Miro/Sierra work")
      }

  /** When we harvest the Calm data into Sierra, the `RecordID` is stored in
    * Marcfield 035$a.
    *
    * Calm IDs follow the UUID spec
    *
    * e.g: f5217b45-b742-472b-95c3-f136d5de1104
    * see: `https://search.wellcomelibrary.org/iii/encore/record/C__Rb1971204?marcData=Y`
    *
    * This field is also used for other "system control numbers" from UKMHL, LSHTM etc.
    * e.g: (OCoLC)927468903, (lshtm)a60032
    * see: `https://search.wellcomelibrary.org/iii/encore/record/C__Rb1187988?marcData=Y`
    */
  private def get035CalmMergeCandidates(
    bibData: SierraBibData): List[MergeCandidate] =
    bibData
      .subfieldsWithTag("035" -> "a")
      .contents
      .flatMap(str => Try(UUID.fromString(str)).toOption)
      .map { recordId =>
        MergeCandidate(
          identifier = SourceIdentifier(
            identifierType = IdentifierType("calm-record-id"),
            ontologyType = "Work",
            value = recordId.toString
          ),
          reason = Some("Calm/Sierra harvest")
        )
      }
      .distinct

  private def matching962Ids(bibData: SierraBibData) =
    bibData
      .subfieldsWithTag("962" -> "u")
      .contents
      .flatMap { maybeGetMiroID }
      .distinct

  private def matching089Ids(bibData: SierraBibData) =
    bibData
      .subfieldsWithTag("089" -> "a")
      .contents
      .flatMap { parse089MiroId }

  private def miroMergeCandidate(miroId: String, reason: String) = {
    MergeCandidate(
      identifier = SourceIdentifier(
        identifierType = IdentifierType("miro-image-number"),
        ontologyType = "Work",
        value = miroId
      ),
      reason = Some(reason)
    )
  }
}
