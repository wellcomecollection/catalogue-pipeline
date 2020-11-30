package uk.ac.wellcome.models.work.internal

sealed trait DeletedReasons

object DeletedReasons {
  case class DeletedFromSource(info: String) extends DeletedReasons
  case class SuppressedFromSource(info: String) extends DeletedReasons
}
