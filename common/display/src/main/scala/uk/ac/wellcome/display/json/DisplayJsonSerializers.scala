package uk.ac.wellcome.display.json

import io.circe.Encoder
import uk.ac.wellcome.display.models.SortingOrder.{Ascending, Descending}
import uk.ac.wellcome.display.models.{
  ProductionDateSortRequest,
  SortRequest,
  SortingOrder
}

trait DisplayJsonSerializers {
  implicit val encodeSortingOrder: Encoder[SortingOrder] =
    Encoder[String].contramap {
      case Ascending  => "asc"
      case Descending => "desc"
    }

  implicit val encodeSortRequest: Encoder[SortRequest] =
    Encoder[String].contramap {
      case ProductionDateSortRequest => "production.dates"
    }
}
