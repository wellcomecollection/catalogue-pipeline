package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.{
  IdentifierType,
  MergeCandidate,
  SourceIdentifier
}
import uk.ac.wellcome.platform.transformer.calm.source.{
  SierraBibData,
  SierraMaterialType,
  SierraQueryOps
}
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.calm.transformers.parsers.{
  MiroIdParser,
  WellcomeImagesURLParser
}

import scala.util.matching.Regex

object SierraMergeCandidates
    extends SierraTransformer
    with SierraQueryOps
    with WellcomeImagesURLParser
    with MiroIdParser {

  type Output = List[MergeCandidate]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    get776mergeCandidates(bibData) ++
      getSinglePageMiroMergeCandidates(bibData)

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

  /** We can merge a single-page Miro and Sierra work if:
    *
    *   - The Sierra work has type "Picture" or "Digital images"
    *   - There's exactly one Miro ID in MARC tag 962 subfield $u
    *     (if there's more than one Miro ID, we can't do a merge).
    *   - There's exactly one item on the Sierra record (if there's more
    *     than one item, we don't know where to put the Miro location).
    *
    */
  private def getSinglePageMiroMergeCandidates(
    bibData: SierraBibData): List[MergeCandidate] =
    bibData.materialType match {
      // The Sierra material type codes we care about are:
      // * k (Pictures)
      // * q (Digital Images)
      case Some(SierraMaterialType("k")) | Some(SierraMaterialType("q")) | Some(
            SierraMaterialType("r")) =>
        (matching962Ids(bibData), matching089Ids(bibData)) match {
          case (List(miroId), _) =>
            List(
              miroMergeCandidate(miroId, "Single page Miro/Sierra work")
            )
          case (List(), List(miroId)) =>
            List(
              miroMergeCandidate(
                miroId,
                "Single page Miro/Sierra work (secondary source)")
            )
          case _ => Nil
        }
      case _ => Nil
    }

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
