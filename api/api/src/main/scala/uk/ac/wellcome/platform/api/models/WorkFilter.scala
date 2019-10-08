package uk.ac.wellcome.platform.api.models

import java.time.LocalDate

sealed trait WorkFilter

case class ItemLocationTypeFilter(locationTypeIds: Seq[String]) extends WorkFilter
object ItemLocationTypeFilter extends ApplyCommaSeperated[ItemLocationTypeFilter] {
  val fromSeq = ItemLocationTypeFilter(_)
}

case class WorkTypeFilter(workTypeIds: Seq[String]) extends WorkFilter
object WorkTypeFilter extends ApplyCommaSeperated[WorkTypeFilter] {
  val fromSeq = WorkTypeFilter(_)
}

case class DateRangeFilter(fromDate: Option[LocalDate],
                           toDate: Option[LocalDate])
    extends WorkFilter

case object IdentifiedWorkFilter extends WorkFilter

trait ApplyCommaSeperated[T] {

  protected val fromSeq: Seq[String] =>  T

  def apply(str: String): T =
    fromSeq(str.split(',').map(_.trim))
}
