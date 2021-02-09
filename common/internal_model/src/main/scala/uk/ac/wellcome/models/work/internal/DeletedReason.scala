package uk.ac.wellcome.models.work.internal

sealed trait DeletedReason

object DeletedReason {
  case class DeletedFromSource(info: String) extends DeletedReason
  case class SuppressedFromSource(info: String) extends DeletedReason
}
