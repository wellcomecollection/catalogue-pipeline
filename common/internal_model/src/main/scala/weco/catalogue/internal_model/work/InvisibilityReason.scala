package weco.catalogue.internal_model.work

sealed trait InvisibilityReason

object InvisibilityReason {
  case class CopyrightNotCleared(info: String) extends InvisibilityReason
  case class SourceFieldMissing(info: String) extends InvisibilityReason
  case class InvalidValueInSourceField(info: String) extends InvisibilityReason
  case object UnlinkedHistoricalLibraryMiro extends InvisibilityReason
  case class UnableToTransform(message: String) extends InvisibilityReason
  case object MetsWorksAreNotVisible extends InvisibilityReason
}
