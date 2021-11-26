package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}

trait MergeCandidateGenerators {
  def createSierraPairMergeCandidateFor(
    w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] = {
    require(
      w.state.sourceIdentifier.identifierType == IdentifierType.SierraSystemNumber)

    MergeCandidate(
      id = IdState.Identified(
        canonicalId = w.state.canonicalId,
        sourceIdentifier = w.state.sourceIdentifier
      ),
      reason = "Physical/digitised Sierra work"
    )
  }

  def createMetsMergeCandidateFor(
    w: Work[WorkState.Identified]): MergeCandidate[IdState.Identified] = {
    require(
      w.state.sourceIdentifier.identifierType == IdentifierType.SierraSystemNumber)

    MergeCandidate(
      id = IdState.Identified(
        canonicalId = w.state.canonicalId,
        sourceIdentifier = w.state.sourceIdentifier
      ),
      reason = "METS work"
    )
  }
}
