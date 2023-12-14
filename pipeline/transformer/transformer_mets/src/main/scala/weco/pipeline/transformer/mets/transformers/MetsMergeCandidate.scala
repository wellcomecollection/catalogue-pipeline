package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.MergeCandidate
import weco.pipeline.transformer.identifiers.IdentifierRegexes.sierraSystemNumber
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._

/** A Merge candidate from METS can be a record from either
  *   - CALM (for Archivematica / Born Digital)
  *   - Sierra (for Goobi / Digitised)
  */
object MetsMergeCandidate {

  def apply(recordIdentifier: String): MergeCandidate[IdState.Identifiable] = {
    recordIdentifier match {
      // It's slightly crude to make this guess based solely on the format of the
      // identifier, as any path-like string could look like a CALM refno.
      // However, it should work for the values we expect to be given here.
      case bnumber
          if sierraSystemNumber.findFirstIn(bnumber.toLowerCase).isDefined =>
        mergeCandidate(
          identifierType = IdentifierType.SierraSystemNumber,
          identifier = recordIdentifier.toLowerCase
        )
      case _ =>
        mergeCandidate(
          identifierType = IdentifierType.CalmRefNo,
          identifier = recordIdentifier
        )
    }
  }
  private def mergeCandidate(
    identifierType: IdentifierType,
    identifier: String
  ): MergeCandidate[IdState.Identifiable] = MergeCandidate(
    identifier = SourceIdentifier(
      identifierType = identifierType,
      ontologyType = "Work",
      value = identifier
    ).validatedWithWarning.getOrElse(
      throw new RuntimeException(
        s"METS works must have a valid CALM or Sierra merge candidate: ${identifier.toLowerCase} is not valid."
      )
    ),
    reason = "METS work"
  )

}
