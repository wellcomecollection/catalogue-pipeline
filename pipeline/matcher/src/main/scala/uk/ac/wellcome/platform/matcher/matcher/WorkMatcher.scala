package uk.ac.wellcome.platform.matcher.matcher

import java.util.UUID

import cats.instances.try_._
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult, WorkIdentifier, WorkNode}
import uk.ac.wellcome.models.work.internal.{TransformedBaseWork, UnidentifiedInvisibleWork, UnidentifiedWork}
import uk.ac.wellcome.platform.matcher.models._
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.platform.matcher.workgraph.WorkGraphUpdater
import uk.ac.wellcome.storage.{LockDao, LockingService}

import scala.util.{Failure, Success, Try}

class WorkMatcher(
  workGraphStore: WorkGraphStore,
  lockingService: LockingService[Set[MatchedIdentifiers], Try, LockDao[String, UUID]])
    extends Logging {

  def matchWork(work: TransformedBaseWork): Try[MatcherResult] = work match {
    case w: UnidentifiedWork =>
      doMatch(w).map(MatcherResult)
    case w: UnidentifiedInvisibleWork =>
      Success(singleMatchedIdentifier(w))
  }

  private def doMatch(work: UnidentifiedWork): Try[Set[MatchedIdentifiers]] = {
    val update = WorkUpdate(work)

    val updateAffectedIdentifiers = update.referencedWorkIds + update.workId

    val lockResult: Try[lockingService.Process] = lockingService.withLocks(updateAffectedIdentifiers) {
      withUpdateLocked(update, updateAffectedIdentifiers, affectedWork = work)
    }

    handleLockingResult(lockResult, affectedWork = work)
  }

  private def handleLockingResult(lockResult: Try[lockingService.Process], affectedWork: UnidentifiedWork): Try[Set[MatchedIdentifiers]] =
    lockResult match {
      case Failure(err) =>
        error(s"Locking failure while matching work ${affectedWork.sourceIdentifier}: $err")
        Failure(err)
      case Success(Left(failedLockingServiceOp)) =>
        error(s"Failed locking service op ${affectedWork.sourceIdentifier}: $failedLockingServiceOp")
        Failure(new Throwable(s"Failed locking service op: $failedLockingServiceOp"))
      case Success(Right(value)) =>
        Success(value)
    }

  private def singleMatchedIdentifier(work: UnidentifiedInvisibleWork) = {
    MatcherResult(
      Set(
        MatchedIdentifiers(Set(WorkIdentifier(work)))
      )
    )
  }

  private def withUpdateLocked(
    update: WorkUpdate,
    updateAffectedIdentifiers: Set[String],
    affectedWork: UnidentifiedWork): Try[Set[MatchedIdentifiers]] = {
    workGraphStore.findAffectedWorks(update).flatMap { existingGraph =>
      val updatedGraph = WorkGraphUpdater.update(update, existingGraph)

      val lockingResult = lockingService.withLocks(existingGraph.nodes.map(_.id) -- updateAffectedIdentifiers) {
        workGraphStore.put(updatedGraph).map { _ =>
          convertToIdentifiersList(updatedGraph)
        }
      }

      handleLockingResult(lockingResult, affectedWork = affectedWork)
    }
  }

  private def convertToIdentifiersList(graph: WorkGraph): Set[MatchedIdentifiers] = {
    groupBySetId(graph).map {
      case (_, workNodes: Set[WorkNode]) =>
        MatchedIdentifiers(workNodes.map(WorkIdentifier(_)))
    }.toSet
  }

  private def groupBySetId(updatedGraph: WorkGraph): Map[String, Set[WorkNode]] =
    updatedGraph.nodes.groupBy { _.componentId }
}
