package uk.ac.wellcome.platform.matcher.storage

import org.scanamo.error.DynamoReadError
import scala.concurrent.{ExecutionContext, Future}

import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(workUpdate: WorkUpdate): Future[WorkGraph] =
    for {
      directlyAffectedWorks <- workNodeDao.get(workUpdate.ids)
      affectedComponentIds = directlyAffectedWorks.map(workNode =>
        workNode.componentId)
      affectedWorks <- workNodeDao.getByComponentIds(affectedComponentIds)
    } yield WorkGraph(affectedWorks)

  def put(graph: WorkGraph)
    : Future[Set[Option[Either[DynamoReadError, WorkNode]]]] = {
    Future.sequence(
      graph.nodes.map(workNodeDao.put)
    )
  }
}
