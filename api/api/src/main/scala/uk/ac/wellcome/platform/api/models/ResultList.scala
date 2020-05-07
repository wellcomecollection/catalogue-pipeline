package uk.ac.wellcome.platform.api.models

case class ResultList[Result, Aggs](
  results: List[Result],
  totalResults: Int,
  aggregations: Option[Aggs]
)
