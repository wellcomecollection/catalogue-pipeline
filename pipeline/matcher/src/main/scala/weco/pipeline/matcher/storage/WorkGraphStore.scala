package weco.pipeline.matcher.storage

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.models.WorkNode

import scala.concurrent.{ExecutionContext, Future}

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(ids: Set[CanonicalId]): Future[Set[WorkNode]] =
    for {
      directlyAffectedWorks <- workNodeDao.get(ids)
      subgraphIds = directlyAffectedWorks.map(_.subgraphId)
      affectedWorks <- workNodeDao.getBySubgraphIds(subgraphIds)
    } yield affectedWorks

  def put(nodes: Set[WorkNode]): Future[Unit] =
    workNodeDao.put(nodes)
}
