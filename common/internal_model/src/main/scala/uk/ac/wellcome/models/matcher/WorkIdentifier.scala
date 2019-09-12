package uk.ac.wellcome.models.matcher

import uk.ac.wellcome.models.work.internal.BaseWork

case class WorkIdentifier(identifier: String, version: Option[Int])

case object WorkIdentifier {
  def apply(work: WorkNode): WorkIdentifier =
    WorkIdentifier(work.id, work.version)

  def apply(work: BaseWork): WorkIdentifier =
    WorkIdentifier(
      identifier = work.sourceIdentifier.toString,
      version = Some(work.version)
    )

  def apply(identifier: String, version: Int): WorkIdentifier =
    WorkIdentifier(identifier, Some(version))
}
