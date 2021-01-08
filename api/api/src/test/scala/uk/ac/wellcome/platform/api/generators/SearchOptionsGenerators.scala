package uk.ac.wellcome.platform.api.generators

import uk.ac.wellcome.display.models.{
  AggregationRequest,
  SortRequest,
  SortingOrder
}
import uk.ac.wellcome.platform.api.models._

trait SearchOptionsGenerators {
  def createWorksSearchOptionsWith(
    filters: List[WorkFilter] = Nil,
    pageSize: Int = 10,
    pageNumber: Int = 1,
    aggregations: List[AggregationRequest] = Nil,
    sort: List[SortRequest] = Nil,
    sortOrder: SortingOrder = SortingOrder.Ascending,
    searchQuery: Option[SearchQuery] = None
  ): SearchOptions[WorkFilter, WorkMustQuery] =
    SearchOptions(
      filters = filters,
      pageSize = pageSize,
      pageNumber = pageNumber,
      aggregations = aggregations,
      sortBy = sort,
      sortOrder = sortOrder,
      searchQuery = searchQuery
    )

  def createWorksSearchOptions: SearchOptions[WorkFilter, WorkMustQuery] =
    createWorksSearchOptionsWith()
}
