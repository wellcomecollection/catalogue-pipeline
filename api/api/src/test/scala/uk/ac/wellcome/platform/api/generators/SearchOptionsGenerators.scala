package uk.ac.wellcome.platform.api.generators

import uk.ac.wellcome.display.models.{
  AggregationRequest,
  SortRequest,
  SortingOrder
}
import uk.ac.wellcome.platform.api.models.WorkFilter
import uk.ac.wellcome.platform.api.services.{
  ElasticsearchQueryOptions,
  WorksSearchOptions
}

trait SearchOptionsGenerators {
  def createElasticsearchQueryOptionsWith(
    filters: List[WorkFilter] = List(),
    limit: Int = 10,
    from: Int = 0,
    aggregations: List[AggregationRequest] = List(),
    sort: List[SortRequest] = List(),
    sortOrder: SortingOrder = SortingOrder.Ascending
  ): ElasticsearchQueryOptions =
    ElasticsearchQueryOptions(
      filters = filters,
      limit = limit,
      from = from,
      aggregations = aggregations,
      sortBy = sort,
      sortOrder = sortOrder
    )

  def createElasticsearchQueryOptions: ElasticsearchQueryOptions =
    createElasticsearchQueryOptionsWith()

  def createWorksSearchOptionsWith(
    filters: List[WorkFilter] = List(),
    pageSize: Int = 10,
    pageNumber: Int = 1,
    aggregations: List[AggregationRequest] = List(),
    sort: List[SortRequest] = List(),
    sortOrder: SortingOrder = SortingOrder.Ascending
  ): WorksSearchOptions =
    WorksSearchOptions(
      filters = filters,
      pageSize = pageSize,
      pageNumber = pageNumber,
      aggregations = aggregations,
      sortBy = sort,
      sortOrder = sortOrder
    )

  def createWorksSearchOptions: WorksSearchOptions =
    createWorksSearchOptionsWith()
}
