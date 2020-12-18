package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.IdState.Identifiable

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
import uk.ac.wellcome.platform.transformer.sierra.transformers.parsers.MiroIdParsing

import scala.util.Try
import scala.util.matching.Regex

object SierraMergeCandidates extends SierraDataTransformer with SierraQueryOps {

  type Output = List[MergeCandidate[Identifiable]]

  def apply(bibData: SierraBibData) =
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
    bibData: SierraBibData): List[MergeCandidate[Identifiable]] =
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
              value = bibNumber.trim
            ),
            reason = "Physical/digitised Sierra work"
          )
        )
      case _ => Nil
    }

  /*
   * We always try to merge all linked Miro and Sierra works
   */
  private def getSinglePageMiroMergeCandidates(
    bibData: SierraBibData): List[MergeCandidate[Identifiable]] = {
    val allIds = (matching089Ids(bibData) ++ matching962Ids(bibData)).distinct
    removeNonSuffixedIfSuffixedExists(allIds).map {
      miroMergeCandidate(_, "Miro/Sierra work")
    }
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
    bibData: SierraBibData): List[MergeCandidate[Identifiable]] =
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
          reason = "Calm/Sierra harvest"
        )
      }
      .distinct

  private def matching962Ids(bibData: SierraBibData) =
    bibData
      .subfieldsWithTag("962" -> "u")
      .contents
      .flatMap { MiroIdParsing.maybeFromURL }
      .distinct

  private def matching089Ids(bibData: SierraBibData) =
    bibData
      .subfieldsWithTag("089" -> "a")
      .contents
      .flatMap { MiroIdParsing.maybeFromString }

  private def miroMergeCandidate(miroId: String, reason: String) = {
    MergeCandidate(
      identifier = SourceIdentifier(
        identifierType = IdentifierType("miro-image-number"),
        ontologyType = "Work",
        value = miroId
      ),
      reason = reason
    )
  }

  // If we have IDs that are identical except for a non-numeric suffix,
  // then we want to use the suffixed IDs over the the non-suffixed ones.
  //
  // eg. if we have V0036036EL and V0036036, we want to remove
  // V0036036 and keep V0036036EL.
  private def removeNonSuffixedIfSuffixedExists(
    ids: List[String]): List[String] =
    ids
      .groupBy(MiroIdParsing.stripSuffix)
      .flatMap {
        case (_, List(singleId)) => List(singleId)
        case (stemId, leafIds)   => leafIds.filterNot(_ == stemId)
      }
      .toList
}
