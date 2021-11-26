package weco.pipeline.matcher.storage

import weco.pipeline.matcher.models.{WorkNode, WorkStub}

import scala.concurrent.{ExecutionContext, Future}

class WorkGraphStore(workNodeDao: WorkNodeDao)(implicit _ec: ExecutionContext) {

  def findAffectedWorks(w: WorkStub): Future[Set[WorkNode]] =
    for {
      directlyAffectedWorks <- workNodeDao.get(w.ids)
      affectedComponentIds = directlyAffectedWorks.map(_.componentId)
      affectedWorks <- workNodeDao.getByComponentIds(affectedComponentIds)
      suppressedWorks <- getSuppressedWorksLinkedFrom(affectedWorks)
    } yield affectedWorks ++ suppressedWorks

  // Suppressed works are singletons in the matcher graph, so we don't find
  // them when searching by component ID -- but we might refer to them in
  // one of the affected works.
  //
  // We want to retrieve the copy of the suppressed node from the graph store,
  // so we don't overwrite them when we store the updated graph.
  //
  // Suppressed works should never be linking to other works, so we only need
  // to do this extra search pass once.
  private def getSuppressedWorksLinkedFrom(affectedWorks: Set[WorkNode]): Future[Set[WorkNode]] = {
    val missingIds = affectedWorks.flatMap(_.linkedIds) -- affectedWorks.map(_.id)

    if (missingIds.isEmpty) {
      Future.successful(Set())
    } else {
      workNodeDao.get(missingIds)
    }
  }

  def put(nodes: Set[WorkNode]): Future[Unit] =
    workNodeDao.put(nodes)
}
