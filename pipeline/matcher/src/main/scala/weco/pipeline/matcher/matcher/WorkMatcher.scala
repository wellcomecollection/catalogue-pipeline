package weco.pipeline.matcher.matcher

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import grizzled.slf4j.Logging
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode,
  WorkStub
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

  def matchWork(work: WorkStub): Future[MatcherResult] =
    withLocks(work, work.ids.map(_.toString)) {
      for {
        beforeNodes <- workGraphStore.findAffectedWorks(work)
        afterNodes = WorkGraphUpdater.update(work, beforeNodes)

        updatedNodes = afterNodes -- beforeNodes

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
              works = toMatchedIdentifiers(afterNodes),
              createdTime = Instant.now()))
        } else {
          val affectedComponentIds =
            (beforeNodes ++ afterNodes)
              .map { _.componentId }

          withLocks(work, ids = affectedComponentIds) {
            workGraphStore
              .put(afterNodes)
              .map(
                _ =>
                  MatcherResult(
                    works = toMatchedIdentifiers(afterNodes),
                    createdTime = Instant.now()))
          }
        }
      } yield matcherResult
    }

  private def withLocks(w: WorkStub, ids: Set[String])(
    f: => Future[MatcherResult]): Future[MatcherResult] =
    lockingService
      .withLocks(ids)(f)
      .map {
        case Left(failure) =>
          debug(s"Locking failed while matching work ${w.id}: $failure")
          throw failureToException(failure)
        case Right(out) => out
      }

  private def failureToException(failure: FailedLockingServiceOp): Throwable =
    failure match {
      case FailedUnlock(_, _, e) => e
      case FailedProcess(_, e)   => e
      case _                     => new RuntimeException(failure.toString)
    }

  private def toMatchedIdentifiers(
    nodes: Set[WorkNode]): Set[MatchedIdentifiers] =
    nodes
      .groupBy { _.componentId }
      .map {
        case (_, workNodes) =>
          // The matcher graph may include nodes for Works it hasn't seen yet, or which
          // don't exist.  These are placeholders, in case we see the Work later -- but we
          // shouldn't expose their existence to other services.
          //
          // We only send identifiers that correspond to real Works.
          val identifiers =
            workNodes
              .collect {
                case WorkNode(id, Some(version), _, _, _) =>
                  WorkIdentifier(id, version)
              }

          MatchedIdentifiers(identifiers)
      }
      .filter { _.identifiers.nonEmpty }
      .toSet
}
