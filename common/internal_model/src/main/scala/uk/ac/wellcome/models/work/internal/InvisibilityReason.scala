package uk.ac.wellcome.models.work.internal

sealed trait InvisibilityReason

object InvisibilityReason {
  case class DeletedFromSource(info: String) extends InvisibilityReason
  case class SuppressedFromSource(info: String) extends InvisibilityReason
  case class CopyrightNotCleared(info: String) extends InvisibilityReason
  case class SourceFieldMissing(info: String) extends InvisibilityReason
  case class InvalidValueInSourceField(info: String) extends InvisibilityReason
}
