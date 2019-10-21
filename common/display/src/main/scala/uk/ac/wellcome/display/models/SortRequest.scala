package uk.ac.wellcome.display.models

sealed trait SortRequest

case object ProductionDateSortRequest extends SortRequest

sealed trait SortingOrder

object SortingOrder {
  case object Ascending extends SortingOrder
  case object Descending extends SortingOrder
}
