package uk.ac.wellcome.models.work.internal

sealed trait Reason

sealed trait SuppressedReason extends Reason
object SuppressedReason {
  case object DeletedFromSierra extends SuppressedReason
  case object SuppressedFromSierra extends SuppressedReason
  case object SuppressedFromCalm extends SuppressedReason
  case object MetsSource extends SuppressedReason
  case object CopyrightNotClearedFromMiro extends SuppressedReason
  case object SourceNotClearedFromMiro extends SuppressedReason
}

sealed trait UntransformableReason extends Reason
object UntransformableReason {
  case class SourceFieldMissing(info: String) extends UntransformableReason
  case class InvalidValueInSourceField(info: String)
      extends UntransformableReason
}
