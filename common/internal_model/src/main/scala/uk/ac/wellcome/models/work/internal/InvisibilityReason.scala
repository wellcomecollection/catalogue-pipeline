package uk.ac.wellcome.models.work.internal

sealed trait InvisibilityReason
object InvisibilityReason {
  case object CalmNoTransmission extends InvisibilityReason
  case object CalmMissingLevel extends InvisibilityReason
  sealed case class CalmInvalidLevel(info: String) extends InvisibilityReason

  case object MetsSource extends InvisibilityReason

  case object MiroShouldNotTransform extends InvisibilityReason

  case object SierraDeleted extends InvisibilityReason
  case object SierraSuppressed extends InvisibilityReason
  case object SierraTitleMissing extends InvisibilityReason
}
