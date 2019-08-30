package uk.ac.wellcome.platform.api.generators

import uk.ac.wellcome.display.models.AggregationRequest
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
    aggregations: List[AggregationRequest] = List()
  ): ElasticsearchQueryOptions =
    ElasticsearchQueryOptions(
      filters = filters,
      limit = limit,
      from = from,
      aggregations = aggregations
    )

  def createElasticsearchQueryOptions: ElasticsearchQueryOptions =
    createElasticsearchQueryOptionsWith()

  def createWorksSearchOptionsWith(
    filters: List[WorkFilter] = List(),
    pageSize: Int = 10,
    pageNumber: Int = 1,
    aggregations: List[AggregationRequest] = List()
  ): WorksSearchOptions =
    WorksSearchOptions(
      filters = filters,
      pageSize = pageSize,
      pageNumber = pageNumber,
      aggregations = aggregations
    )

  def createWorksSearchOptions: WorksSearchOptions =
    createWorksSearchOptionsWith()
}
