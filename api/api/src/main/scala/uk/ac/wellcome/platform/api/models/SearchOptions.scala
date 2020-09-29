package uk.ac.wellcome.platform.api.models

import uk.ac.wellcome.display.models.{
  AggregationRequest,
  SortRequest,
  SortingOrder
}

case class SearchOptions[Filter <: DocumentFilter](
  searchQuery: Option[SearchQuery] = None,
  filters: List[Filter] = Nil,
  aggregations: List[AggregationRequest] = Nil,
  sortBy: List[SortRequest] = Nil,
  sortOrder: SortingOrder = SortingOrder.Ascending,
  pageSize: Int = 10,
  pageNumber: Int = 1
)
