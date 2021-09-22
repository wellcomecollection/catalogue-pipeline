package weco.pipeline.matcher.storage

import weco.pipeline.matcher.models.WorkNode

import scala.concurrent.{ExecutionContext, Future}
import weco.pipeline.matcher.models.WorkLinks

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(workLinks: WorkLinks): Future[Set[WorkNode]] =
    for {
      directlyAffectedWorks <- workNodeDao.get(workLinks.ids)
      affectedComponentIds = directlyAffectedWorks.map(workNode =>
        workNode.componentId)
      affectedWorks <- workNodeDao.getByComponentIds(affectedComponentIds)
    } yield affectedWorks

  def put(nodes: Set[WorkNode]): Future[Unit] =
    workNodeDao.put(nodes)
}
