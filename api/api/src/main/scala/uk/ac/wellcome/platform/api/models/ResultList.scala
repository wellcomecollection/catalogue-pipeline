package uk.ac.wellcome.platform.api.models

import uk.ac.wellcome.models.work.internal.IdentifiedWork

case class WorkResult(work: IdentifiedWork, score: Int, queryName: String)

case class ResultList(
  results: List[WorkResult],
  totalResults: Int,
  aggregations: Option[Aggregations]
)
