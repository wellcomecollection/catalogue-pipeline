package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

case class WorkIdentifier(identifier: CanonicalId, version: Int)

object WorkIdentifier {
  def apply(work: Work[Identified]): WorkIdentifier =
    WorkIdentifier(
      identifier = work.state.canonicalId,
      version = work.version
    )
}
