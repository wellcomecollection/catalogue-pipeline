package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

case class WorkIdentifier(identifier: CanonicalId, version: Option[Int])

object WorkIdentifier {
  def apply(work: WorkNode): WorkIdentifier =
    WorkIdentifier(work.id, work.version)

  def apply(work: Work[Identified]): WorkIdentifier =
    WorkIdentifier(
      identifier = work.state.canonicalId,
      version = Some(work.version)
    )

  def apply(identifier: CanonicalId, version: Int): WorkIdentifier =
    WorkIdentifier(identifier, Some(version))
}
