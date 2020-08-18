package uk.ac.wellcome.platform.matcher.matcher

import cats.implicits._
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.models._
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.platform.matcher.workgraph.WorkGraphUpdater
import uk.ac.wellcome.storage.locking.dynamo.DynamoLockingService
import uk.ac.wellcome.storage.locking.{
  FailedLockingServiceOp,
  FailedProcess,
  FailedUnlock
}

import scala.concurrent.{ExecutionContext, Future}

class WorkMatcher(
  workGraphStore: WorkGraphStore,
  lockingService: DynamoLockingService[Set[MatchedIdentifiers], Future])(
  implicit ec: ExecutionContext)
    extends Logging {

  type Out = Set[MatchedIdentifiers]

  def matchWork(work: TransformedBaseWork): Future[MatcherResult] =
    doMatch(work).map(MatcherResult)

  private def doMatch(work: TransformedBaseWork): Future[Out] = {
    val update = WorkUpdate(work)
    withLocks(update, update.ids) {
      for {
        graphBeforeUpdate <- workGraphStore.findAffectedWorks(update)
        updatedGraph = WorkGraphUpdater.update(update, graphBeforeUpdate)
        _ <- withLocks(update, graphBeforeUpdate.nodes.map(_.componentId)) {
          // We are returning empty set here, as LockingService is tied to a
          // single `Out` type, here set to `Set[MatchedIdentifiers]`.
          // See issue here: https://github.com/wellcometrust/platform/issues/3873
          workGraphStore.put(updatedGraph).map(_ => Set.empty)
        }
      } yield {
        convertToIdentifiersList(updatedGraph)
      }
    }
  }

  private def withLocks(update: WorkUpdate, ids: Set[String])(
    f: => Future[Out]): Future[Out] =
    lockingService
      .withLocks(ids)(f)
      .map {
        case Left(failure) => {
          debug(
            s"Locking failed while matching work ${update.workId}: ${failure}")
          throw MatcherException(failureToException(failure))
        }
        case Right(out) => out
      }

  private def failureToException(failure: FailedLockingServiceOp): Throwable =
    failure match {
      case FailedUnlock(_, _, e) => e
      case FailedProcess(_, e)   => e
      case _                     => new RuntimeException(failure.toString)
    }

  private def convertToIdentifiersList(graph: WorkGraph) = {
    groupBySetId(graph).map {
      case (_, workNodes: Set[WorkNode]) =>
        MatchedIdentifiers(workNodes.map(WorkIdentifier(_)))
    }.toSet
  }

  private def groupBySetId(updatedGraph: WorkGraph) =
    updatedGraph.nodes.groupBy(_.componentId)
}
