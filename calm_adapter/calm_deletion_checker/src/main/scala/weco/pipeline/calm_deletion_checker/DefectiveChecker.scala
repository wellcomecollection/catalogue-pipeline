package weco.pipeline.calm_deletion_checker

import grizzled.slf4j.Logging
import weco.pipeline.calm_api_client.CalmApiClient
import weco.catalogue.source_model.CalmSourcePayload
import weco.pipeline.calm_api_client.{CalmApiClient, CalmQuery, CalmSession}

import scala.concurrent.{ExecutionContext, Future}

/*
 * With a `test` method implemented to return the number of defectives in a
 * set of Items, a DefectiveChecker provides `defectiveRecords` which returns
 * a set of all defectives in the given items.
 *
 * The implementation here is based on the algorithm given in Wang et al (2017)
 * Found at: https://arxiv.org/abs/1407.2283, see `Algorithm 1`
 * While there are more optimal algorithms that exist, the implementation of
 * this one is very simple.
 */
trait DefectiveChecker[Item] {
  protected def test(items: Set[Item]): Future[Int]

  def defectiveRecords(
    allItems: Set[Item]
  )(implicit ec: ExecutionContext): Future[Set[Item]] = {
    def nested(items: Set[Item], d: Int): Future[Set[Item]] = d match {
      case 0                    => Future.successful(Set.empty)
      case n if n == items.size => Future.successful(items)
      case _ =>
        val testSet = items.take(M(items.size, d))
        for {
          d1 <- test(testSet)
          deletions1 <- nested(testSet, d1)
          deletions2 <- nested(items -- testSet, d - d1)
        } yield deletions1 ++ deletions2
    }

    test(allItems).flatMap { d =>
      nested(allItems, d)
    }
  }

  // See equations (6), (7) in https://arxiv.org/abs/1407.2283
  private def l(n: Int, d: Int): Int = math.ceil(log2(n.toFloat / d)).toInt - 1
  private def k(n: Int, d: Int): Int =
    math.ceil(n / math.pow(2, l(n, d))).toInt - d

  // See equation (11)
  private def M(n: Int, d: Int): Int =
    if (d <= n / 2) {
      n - math.pow(2, l(n, d)).toInt * (d + k(n, d) - 1)
    } else M(n, n - d)

  // See equation (5) in https://arxiv.org/abs/1407.2283
  // To account for the initial count (outside of the algorithm proper)
  // we remove the (-1) term and ensure a minimum of 1 test.
  //
  // This is only used for testing the implementation
  def nTestsUpperBound(n: Int, d: Int): Int =
    math.max((l(n, d) + 1) * d + k(n, d), 1)

  private def log2(x: Float): Float = (math.log(x) / math.log(2)).toFloat
}

class ApiDeletionChecker(calmApiClient: CalmApiClient)(implicit
  ec: ExecutionContext
) extends DefectiveChecker[CalmSourcePayload]
    with Logging {

  def test(records: Set[CalmSourcePayload]): Future[Int] =
    for {
      session <- calmApiClient
        .search(
          records
            .map(record => CalmQuery.RecordId(record.id))
            .reduce[CalmQuery](_ or _)
        )
      // Performing a search creates a new session which we'll never use.
      // Abandon it here to give the Calm API a chance.
      _ <- calmApiClient.abandon(session.cookie).recover { case abandonError =>
        warn(s"Error abandoning session: ${abandonError.getMessage}")
      }
    } yield session match {
      case CalmSession(n, _) if n <= records.size => records.size - n
      case CalmSession(n, _) =>
        throw new RuntimeException(
          s"More results returned ($n) than searched for (${records.size}): this should never happen!"
        )
    }

}
