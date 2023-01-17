package weco.catalogue.internal_model.work

sealed trait DeletedReason

object DeletedReason {
  case class DeletedFromSource(info: String) extends DeletedReason
  case class SuppressedFromSource(info: String) extends DeletedReason
  case object TeiDeletedInMerger extends DeletedReason
}
