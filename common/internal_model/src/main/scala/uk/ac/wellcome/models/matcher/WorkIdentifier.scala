package uk.ac.wellcome.models.matcher

import weco.catalogue.internal_model.work.Work

case class WorkIdentifier(identifier: String, version: Option[Int])

object WorkIdentifier {
  def apply(work: WorkNode): WorkIdentifier =
    WorkIdentifier(work.id, work.version)

  def apply(work: Work[_]): WorkIdentifier =
    WorkIdentifier(
      identifier = work.id,
      version = Some(work.version)
    )

  def apply(identifier: String, version: Int): WorkIdentifier =
    WorkIdentifier(identifier, Some(version))
}
