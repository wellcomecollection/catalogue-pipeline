package uk.ac.wellcome.models.matcher

import weco.catalogue.internal_model.identifiers.CanonicalID
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

case class WorkIdentifier(identifier: CanonicalID, version: Option[Int])

object WorkIdentifier {
  def apply(work: WorkNode): WorkIdentifier =
    WorkIdentifier(work.id, work.version)

  def apply(work: Work[Identified]): WorkIdentifier =
    WorkIdentifier(
      identifier = work.state.canonicalId,
      version = Some(work.version)
    )

  def apply(identifier: CanonicalID, version: Int): WorkIdentifier =
    WorkIdentifier(identifier, Some(version))
}
