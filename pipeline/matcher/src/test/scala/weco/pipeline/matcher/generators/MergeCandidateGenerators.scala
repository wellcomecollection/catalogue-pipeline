package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}

trait MergeCandidateGenerators {
  private def createMergeCandidate(
    w: Work[WorkState.Identified],
    identifierType: IdentifierType,
    reason: String
  ): MergeCandidate[IdState.Identified] = {
    require(
      w.state.sourceIdentifier.identifierType == identifierType)

    MergeCandidate(
      id = IdState.Identified(
        canonicalId = w.state.canonicalId,
        sourceIdentifier = w.state.sourceIdentifier
      ),
      reason = reason
    )
  }

  def createSierraPairMergeCandidateFor(
    w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] =
    createMergeCandidate(w, IdentifierType.SierraSystemNumber, reason = "Physical/digitised Sierra work")

  def createCalmMergeCandidateFor(w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] =
    createMergeCandidate(w, IdentifierType.CalmRecordIdentifier, reason = "Calm/Sierra harvest")

  def createCalmMiroMergeCandidateFor(w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] =
    createMergeCandidate(w, IdentifierType.MiroImageNumber, reason = "CALM/Miro work")

  def createMiroSierraMergeCandidateFor(w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] =
    createMergeCandidate(w, IdentifierType.MiroImageNumber, reason = "Miro/Sierra work")

  def createMetsMergeCandidateFor(
    w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] =
    createMergeCandidate(w, IdentifierType.SierraSystemNumber, reason = "METS work")

  def createTeiBnumberMergeCandidateFor(w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] =
    createMergeCandidate(w, IdentifierType.SierraSystemNumber, reason = "Bnumber present in TEI file")
}
