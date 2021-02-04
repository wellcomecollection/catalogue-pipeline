package uk.ac.wellcome.calm_adapter

import akka.NotUsed
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.calm_api_client._

trait CalmRetriever {
  def apply(query: CalmQuery): Source[CalmRecord, NotUsed]
}

class ApiCalmRetriever(
  apiClient: CalmApiClient,
  concurrentHttpConnections: Int = 2,
  suppressedFields: Set[String] = Set.empty
) extends CalmRetriever
    with Logging {

  def apply(query: CalmQuery): Source[CalmRecord, NotUsed] =
    Source
      .future(apiClient.search(query))
      .mapConcat {
        case CalmSession(numHits, cookie) =>
          info(s"Received $numHits records for query: ${query.queryExpression}")
          (0 until numHits).map(pos => (pos, cookie))
      }
      .mapAsync(concurrentHttpConnections) {
        case (pos, cookie) =>
          info(s"Querying record $pos for query: ${query.queryExpression}")
          apiClient.summary(pos, Some(cookie))
      }

  private implicit val suppressedSummaryParser
    : CalmHttpResponseParser[CalmSummaryRequest] =
    CalmHttpResponseParser.createSummaryResponseParser(suppressedFields)
}
