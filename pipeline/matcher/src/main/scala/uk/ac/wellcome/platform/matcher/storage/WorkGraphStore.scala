package uk.ac.wellcome.platform.matcher.storage

import scala.concurrent.{ExecutionContext, Future}

import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkLinks}

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(workLinks: WorkLinks): Future[WorkGraph] =
    for {
      directlyAffectedWorks <- workNodeDao.get(workLinks.ids)
      affectedComponentIds = directlyAffectedWorks.map(workNode =>
        workNode.componentId)
      affectedWorks <- workNodeDao.getByComponentIds(affectedComponentIds)
    } yield WorkGraph(affectedWorks)

  def put(graph: WorkGraph): Future[Unit] =
    workNodeDao.put(graph.nodes)
}
