package weco.pipeline.matcher.matcher

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import weco.pipeline.matcher.exceptions.MatcherException
import weco.pipeline.matcher.models.{WorkGraph, WorkLinks}
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

class WorkMatcher(workGraphStore: WorkGraphStore,
                  lockingService: LockingService[Set[MatchedIdentifiers],
                                                 Future,
                                                 LockDao[String, UUID]])(
  implicit ec: ExecutionContext)
    extends Logging {

  type Out = Set[MatchedIdentifiers]

  def matchWork(links: WorkLinks): Future[MatcherResult] =
    doMatch(links)
      .map { works =>
        MatcherResult(works = works, createdTime = Instant.now())
      }

  private def doMatch(links: WorkLinks): Future[Out] =
    withLocks(links, links.ids.map(_.toString)) {
      for {
        beforeGraph <- workGraphStore.findAffectedWorks(links)
        afterGraph = WorkGraphUpdater.update(links, beforeGraph)

        updatedNodes = afterGraph.nodes -- beforeGraph.nodes

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
        _ <- if (updatedNodes.isEmpty) {
          Future.successful(())
        } else {
          val affectedComponentIds =
            (beforeGraph.nodes ++ afterGraph.nodes)
              .map { _.componentId }

          withLocks(links, ids = affectedComponentIds) {
            // We are returning empty set here, as LockingService is tied to a
            // single `Out` type, here set to `Set[MatchedIdentifiers]`.
            // See issue here: https://github.com/wellcometrust/platform/issues/3873
            workGraphStore.put(afterGraph).map(_ => Set.empty)
          }
        }
      } yield {
        toMatchedIdentifiers(afterGraph)
      }
    }

  private def withLocks(links: WorkLinks, ids: Set[String])(
    f: => Future[Out]): Future[Out] =
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

  private def toMatchedIdentifiers(g: WorkGraph): Set[MatchedIdentifiers] =
    g.nodes
      .groupBy { _.componentId }
      .map {
        case (_, workNodes) =>
          MatchedIdentifiers(workNodes.map(WorkIdentifier(_)))
      }
      .toSet
}
