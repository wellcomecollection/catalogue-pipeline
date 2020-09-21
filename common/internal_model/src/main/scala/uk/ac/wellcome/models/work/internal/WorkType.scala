package uk.ac.wellcome.models.work.internal

sealed trait WorkType

object WorkType {
  case object Standard extends WorkType
  case object Collection extends WorkType
  case object Series extends WorkType
  case object Section extends WorkType
}
