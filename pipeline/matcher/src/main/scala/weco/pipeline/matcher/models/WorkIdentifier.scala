package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

import java.time.Instant

/** Represents a Work in the matcher graph.
  *
  * The modifiedTime is optional because this might represent a Work we
  * haven't seen yet.  e.g. if we got a Work A that referred to B:
  *
  *       A --> B
  *
  * then we'd create a WorkIdentifier for A and B, even though we haven't
  * seen B yet.  This means that if/when we see B, we'll remember its
  * association with A.
  *
  */
case class WorkIdentifier(identifier: CanonicalId, modifiedTime: Option[Instant])

object WorkIdentifier {
  def apply(work: WorkNode): WorkIdentifier =
    WorkIdentifier(work.id, work.modifiedTime)

  def apply(work: Work[Identified]): WorkIdentifier =
    WorkIdentifier(
      identifier = work.state.canonicalId,
      modifiedTime = Some(work.state.modifiedTime)
    )

  def apply(work: WorkStub): WorkIdentifier =
    WorkIdentifier(identifier = work.id, modifiedTime = work.modifiedTime)

  def apply(identifier: CanonicalId, modifiedTime: Instant): WorkIdentifier =
    WorkIdentifier(identifier, Some(modifiedTime))
}
