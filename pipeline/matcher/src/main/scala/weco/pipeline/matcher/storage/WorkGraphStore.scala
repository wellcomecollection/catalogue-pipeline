package weco.pipeline.matcher.storage

import weco.pipeline.matcher.models.WorkNode

import scala.concurrent.{ExecutionContext, Future}
import weco.pipeline.matcher.models.{WorkGraph, WorkStub}

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(work: WorkStub): Future[WorkGraph] =
    for {
      directlyAffectedWorks <- workNodeDao.get(work.ids)
      affectedComponentIds = directlyAffectedWorks.map(workNode =>
        workNode.componentId)
      affectedWorks <- workNodeDao.getByComponentIds(affectedComponentIds)
    } yield WorkGraph(affectedWorks)

  def put(graph: WorkGraph): Future[Unit] =
    put(graph.nodes)

  def put(nodes: Set[WorkNode]): Future[Unit] =
    workNodeDao.put(nodes)
}
