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

  def matchWork(work: WorkStub): Future[MatcherResult] = {
    // We start by locking over all the IDs we know are affected by this
    // particular Work, to stop another matcher process interfering.
    val initialLockIds = work.ids.map(_.toString)

    withLocks(work, initialLockIds) {
      for {
        beforeNodes <- workGraphStore.findAffectedWorks(work.ids)
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
          val result = MatcherResult(
            works = toMatchedIdentifiers(afterNodes),
            createdTime = Instant.now()
          )

          Future.successful(result)
        } else {
          writeUpdate(work, beforeNodes, afterNodes)
        }

      } yield matcherResult
    }
  }

  private def writeUpdate(
    work: WorkStub,
    beforeNodes: Set[WorkNode],
    afterNodes: Set[WorkNode]): Future[MatcherResult] = {

    // We lock over all the subgraphs we're modifying.
    //
    // We don't lock over the individual work IDs, because this can cause a
    // lot of writes to the lock table -- the subgraph IDs should be sufficient.
    //
    val affectedSubgraphIds =
    (beforeNodes ++ afterNodes)
      .map { _.subgraphId }

    withLocks(work, ids = affectedSubgraphIds) {
      for {

        // It's possible that another process has updated a node in a part of the
        // graph we didn't initially lock over.
        //
        // e.g. if we have a graph C->B->A and we're processing C, we wouldn't
        // get a lock on A -- and another process could have updated it since.
        //
        // Check our graph store data is still correct -- if it's stale, we should
        // bail out and wait for the SQS logic to retry us rather than recover.
        refreshedBeforeNodes <- workGraphStore.findAffectedWorks(work.ids)
        _ <- if (refreshedBeforeNodes == beforeNodes) {
          workGraphStore.put(afterNodes)
        } else {
          val t = new RuntimeException(
            s"Error processing ${work.id}: graph store contents changed during matching"
          )
          Future.failed(t)
        }

        result = MatcherResult(
          works = toMatchedIdentifiers(afterNodes),
          createdTime = Instant.now()
        )
      } yield result
    }
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
      .groupBy { _.componentIds }
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
                case WorkNode(id, _, _, Some(sourceWorkData)) =>
                  WorkIdentifier(id, sourceWorkData.version)
              }

          MatchedIdentifiers(identifiers)
      }
      .filter { _.identifiers.nonEmpty }
      .toSet
}
