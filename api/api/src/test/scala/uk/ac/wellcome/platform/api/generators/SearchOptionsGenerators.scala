package uk.ac.wellcome.platform.api.generators

import uk.ac.wellcome.display.models.{
  AggregationRequest,
  SortRequest,
  SortingOrder
}
import uk.ac.wellcome.platform.api.models.{DocumentFilter, SearchQuery}
import uk.ac.wellcome.platform.api.services.{
  ElasticsearchQueryOptions,
  WorksSearchOptions
}

trait SearchOptionsGenerators {
  def createElasticsearchQueryOptionsWith(
    filters: List[DocumentFilter] = Nil,
    limit: Int = 10,
    from: Int = 0,
    aggregations: List[AggregationRequest] = Nil,
    sort: List[SortRequest] = Nil,
    sortOrder: SortingOrder = SortingOrder.Ascending,
    searchQuery: Option[SearchQuery] = None
  ): ElasticsearchQueryOptions =
    ElasticsearchQueryOptions(
      filters = filters,
      limit = limit,
      from = from,
      aggregations = aggregations,
      sortBy = sort,
      sortOrder = sortOrder,
      searchQuery = searchQuery
    )

  def createElasticsearchQueryOptions: ElasticsearchQueryOptions =
    createElasticsearchQueryOptionsWith()

  def createWorksSearchOptionsWith(
    filters: List[DocumentFilter] = Nil,
    pageSize: Int = 10,
    pageNumber: Int = 1,
    aggregations: List[AggregationRequest] = Nil,
    sort: List[SortRequest] = Nil,
    sortOrder: SortingOrder = SortingOrder.Ascending,
    searchQuery: Option[SearchQuery] = None
  ): WorksSearchOptions =
    WorksSearchOptions(
      filters = filters,
      pageSize = pageSize,
      pageNumber = pageNumber,
      aggregations = aggregations,
      sortBy = sort,
      sortOrder = sortOrder,
      searchQuery = searchQuery
    )

  def createWorksSearchOptions: WorksSearchOptions =
    createWorksSearchOptionsWith()
}
