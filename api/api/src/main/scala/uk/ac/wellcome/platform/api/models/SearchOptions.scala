package uk.ac.wellcome.platform.api.models

import uk.ac.wellcome.display.models.{
  ImageAggregationRequest,
  SortRequest,
  SortingOrder,
  WorkAggregationRequest
}

sealed trait SearchOptions[DocumentFilter, AggregationRequest, MustQuery] {
  val searchQuery: Option[SearchQuery]
  val filters: List[DocumentFilter]
  val aggregations: List[AggregationRequest]
  val mustQueries: List[MustQuery]
  val sortBy: List[SortRequest]
  val sortOrder: SortingOrder
  val pageSize: Int
  val pageNumber: Int
}

case class WorkSearchOptions(
  searchQuery: Option[SearchQuery] = None,
  filters: List[WorkFilter] = Nil,
  aggregations: List[WorkAggregationRequest] = Nil,
  mustQueries: List[WorkMustQuery] = Nil,
  sortBy: List[SortRequest] = Nil,
  sortOrder: SortingOrder = SortingOrder.Ascending,
  pageSize: Int = 10,
  pageNumber: Int = 1
) extends SearchOptions[WorkFilter, WorkAggregationRequest, WorkMustQuery]

case class ImageSearchOptions(
  searchQuery: Option[SearchQuery] = None,
  filters: List[ImageFilter] = Nil,
  aggregations: List[ImageAggregationRequest] = Nil,
  mustQueries: List[ImageMustQuery] = Nil,
  sortBy: List[SortRequest] = Nil,
  sortOrder: SortingOrder = SortingOrder.Ascending,
  pageSize: Int = 10,
  pageNumber: Int = 1
) extends SearchOptions[ImageFilter, ImageAggregationRequest, ImageMustQuery]
