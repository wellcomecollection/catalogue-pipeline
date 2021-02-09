package uk.ac.wellcome.platform.calm_deletion_checker

import uk.ac.wellcome.platform.calm_api_client.{
  CalmApiClient,
  CalmQuery,
  CalmSession
}
import weco.catalogue.source_model.CalmSourcePayload

import scala.concurrent.{ExecutionContext, Future}

trait DeletionChecker {
  type Record = CalmSourcePayload
  type Records = Set[Record]

  protected def nDeleted(records: Records): Future[Int]

  // The implementation here is based on the algorithm given in Wang et al (2017)
  // Found at: https://arxiv.org/abs/1407.2283, see `Algorithm 1`
  //
  // While there are more optimal algorithms that exist, the implementation of this one is very simple
  def deletedRecords(batch: Records)(
    implicit ec: ExecutionContext): Future[Records] = {
    def findDeleted(records: Records, d: Int): Future[Records] = d match {
      case 0                      => Future.successful(Set.empty)
      case n if n == records.size => Future.successful(records)
      case _ =>
        val testSet = records.take(M(records.size, d))
        for {
          d1 <- nDeleted(testSet)
          deletions1 <- findDeleted(testSet, d1)
          deletions2 <- findDeleted(records -- testSet, d - d1)
        } yield deletions1 ++ deletions2
    }

    nDeleted(batch).flatMap { d =>
      findDeleted(batch, d)
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
  // We have removed the -1 term to account for the initial
  // count that we must perform .
  //
  // This is only used for testing the implementation
  def upperBound(n: Int, d: Int): Int = (l(n, d) + 1) * d + k(n, d)

  private def log2(x: Float): Float = (math.log(x) / math.log(2)).toFloat
}

class ApiDeletionChecker(calmApiClient: CalmApiClient)(
  implicit ec: ExecutionContext)
    extends DeletionChecker {

  def nDeleted(records: Records): Future[Int] =
    calmApiClient
      .search(
        records
          .map(record => CalmQuery.RecordId(record.id))
          .reduce[CalmQuery](_ or _)
      )
      .map {
        case CalmSession(n, _) if n <= records.size => records.size - n
        case CalmSession(n, _) =>
          throw new RuntimeException(
            s"More results returned ($n) than searched for (${records.size}): this should never happen!")
      }

}
