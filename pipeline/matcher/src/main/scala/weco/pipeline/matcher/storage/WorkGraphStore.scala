package weco.pipeline.matcher.storage

import weco.pipeline.matcher.models.{WorkNode, WorkStub}

import scala.concurrent.{ExecutionContext, Future}

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(w: WorkStub): Future[Set[WorkNode]] =
    for {
      directlyAffectedWorks <- workNodeDao.get(w.ids)
      affectedComponentIds = directlyAffectedWorks.map(_.componentId)
      result <- workNodeDao.getByComponentIds(affectedComponentIds)
    } yield result

  def put(nodes: Set[WorkNode]): Future[Unit] =
    workNodeDao.put(nodes)
}
