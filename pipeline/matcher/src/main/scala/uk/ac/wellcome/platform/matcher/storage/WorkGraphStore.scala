package uk.ac.wellcome.platform.matcher.storage

import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}

import scala.util.{Failure, Success, Try}

class WorkGraphStore(workNodeDao: WorkNodeDao) {
  def findAffectedWorks(workUpdate: WorkUpdate): Try[WorkGraph] = {
    val directlyAffectedWorkIds = workUpdate.referencedWorkIds + workUpdate.workId

    for {
      directlyAffectedWorks <- workNodeDao.get(directlyAffectedWorkIds)
      affectedComponentIds = directlyAffectedWorks.map(workNode =>
        workNode.componentId)
      affectedWorks <- workNodeDao.getByComponentIds(affectedComponentIds)
    } yield WorkGraph(affectedWorks)
  }

  def put(graph: WorkGraph): Try[Set[WorkNode]] = {
    val results: Set[Try[WorkNode]] = graph.nodes.map {
      node => workNodeDao.put(node)
    }

    val successes = results.collect { case Success(s) => s }
    val failures = results.collect { case Failure(err) => err }

    if (failures.isEmpty) {
      Success(successes)
    } else {
      Failure(new Throwable(s"Error putting graph $graph: $failures"))
    }
  }
}
