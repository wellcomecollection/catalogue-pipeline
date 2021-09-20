package weco.pipeline.matcher.matcher

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import grizzled.slf4j.Logging
import weco.pipeline.matcher.exceptions.MatcherException
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkLinks,
  WorkNode
}
import weco.pipeline.matcher.storage.WorkGraphStore
import weco.pipeline.matcher.workgraph.WorkGraphUpdater
import weco.storage.locking.{
  FailedLockingServiceOp,
  FailedProcess,
  FailedUnlock,
  LockDao,
  LockingService
}

import java.time.Instant
import java.util.UUID

class WorkMatcher(
  workGraphStore: WorkGraphStore,
  lockingService: LockingService[MatcherResult, Future, LockDao[String, UUID]])(
  implicit ec: ExecutionContext)
    extends Logging {

  def matchWork(links: WorkLinks): Future[MatcherResult] =
    doMatch(links)

  private def doMatch(links: WorkLinks): Future[MatcherResult] =
    withLocks(links, links.ids.map(_.toString)) {
      for {
        beforeGraph <- workGraphStore.findAffectedWorks(links)
        afterGraph = WorkGraphUpdater.update(links, beforeGraph)

        updatedNodes = afterGraph -- beforeGraph

        // It's possible that the matcher graph hasn't changed -- for example, if
        // we received an update to a work that changes an attribute unrelated to
        // matching/merging.  If so, we can reduce the load we put on the graph
        // store by skipping the write.
        //
        // Note: if the graph has changed at all, we rewrite the whole thing.
        // It's possible we could get away with only writing changed nodes here,
        // but I haven't thought hard enough about whether it might introduce a
        // hard-to-debug consistency error if another process updates the graph
        // between us reading it and writing it.
        matcherResult <- if (updatedNodes.isEmpty) {
          Future.successful(
            MatcherResult(
              works = toMatchedIdentifiers(afterGraph),
              createdTime = Instant.now()))
        } else {
          val affectedComponentIds =
            (beforeGraph ++ afterGraph)
              .map { _.componentId }

          withLocks(links, ids = affectedComponentIds) {
            workGraphStore
              .put(afterGraph)
              .map(
                _ =>
                  MatcherResult(
                    works = toMatchedIdentifiers(afterGraph),
                    createdTime = Instant.now()))
          }
        }
      } yield {
        matcherResult
      }
    }

  private def withLocks(links: WorkLinks, ids: Set[String])(
    f: => Future[MatcherResult]): Future[MatcherResult] =
    lockingService
      .withLocks(ids)(f)
      .map {
        case Left(failure) =>
          debug(s"Locking failed while matching work ${links.workId}: $failure")
          throw MatcherException(failureToException(failure))
        case Right(out) => out
      }

  private def failureToException(failure: FailedLockingServiceOp): Throwable =
    failure match {
      case FailedUnlock(_, _, e) => e
      case FailedProcess(_, e)   => e
      case _                     => new RuntimeException(failure.toString)
    }

  private def toMatchedIdentifiers(nodes: Set[WorkNode]): Set[MatchedIdentifiers] =
    nodes
      .groupBy { _.componentId }
      .map {
        case (_, workNodes) =>
          MatchedIdentifiers(workNodes.map(WorkIdentifier(_)))
      }
      .toSet
}
