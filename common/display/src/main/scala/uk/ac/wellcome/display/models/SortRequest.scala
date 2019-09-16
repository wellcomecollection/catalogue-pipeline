package uk.ac.wellcome.display.models

import uk.ac.wellcome.display.serialize.InvalidStringKeyException

case class InvalidSortRequest(key: String) extends InvalidStringKeyException

case class SortsRequest(values: List[SortRequest])
sealed trait SortRequest
case object ProductionDateFromSortRequest extends SortRequest
case object ProductionDateToSortRequest extends SortRequest
case object ProductionDateSortRequest extends SortRequest

object SortRequest {
  def apply(str: String): Either[InvalidSortRequest, SortRequest] =
    str match {
      case "production.dates.from" => Right(ProductionDateFromSortRequest)
      case "production.dates.to"   => Right(ProductionDateToSortRequest)
      case "production.dates"      => Right(ProductionDateSortRequest)
      case _                       => Left(InvalidSortRequest(str))
    }
}

sealed trait SortingOrder

object SortingOrder {
  case object Ascending extends SortingOrder
  case object Descending extends SortingOrder
}
