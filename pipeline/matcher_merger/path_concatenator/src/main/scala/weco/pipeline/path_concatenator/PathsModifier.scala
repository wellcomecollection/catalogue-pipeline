package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Merged
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

/** Given a path, Fetch and modify the relevant Works (if necessary)
  *   - Modify the work with that exact path.
  *   - Modify all works under that exact path.
  *
  * To do this, it needs to also find the path of the record representing the
  * first node in this path.
  *
  * So, given records with paths:
  *   - root
  *   - root/branch
  *   - leaf/blade
  *   - leaf/tip
  *   - branch/leaf
  *
  * When RecordModifier encounters branch/leaf, it will
  *   - fetch the path root/branch
  *   - change branch/leaf to root/branch/leaf
  *   - change leaf/blade and leaf/tip to root/branch/leaf/blade and
  *     root/branch/leaf/tip, respectively.
  */
case class PathsModifier(pathsService: PathsService)(
  implicit ec: ExecutionContext
) extends Logging {
  import PathOps._
  def modifyPaths(path: String): Future[Seq[Work.Visible[Merged]]] = {
    modifyCurrentPath(path) flatMap {
      case None => modifyChildPaths(path)
      case Some(modifiedWork) =>
        modifyChildPaths(modifiedWork.data.collectionPath.get.path) flatMap {
          childWorks: Seq[Work.Visible[Merged]] =>
            Future(childWorks :+ modifiedWork)
        }
    }
  }

  private def modifyCurrentPath(
    path: String
  ): Future[Option[Work.Visible[Merged]]] =
    pathsService.getParentPath(path) flatMap {
      // If the parent of the current path is not root,
      // then the work corresponding to this path needs to
      // be modified in order to contain the path all the
      // way up to the root.
      case Some(parentPath) =>
        pathsService.getWorkWithPath(path) map {
          work: Work.Visible[Merged] =>
            Some(ChildWork(parentPath, work))
        }
      // This is expected and common.
      // If the current path represents a child of the root,
      // then there are no paths that end with the parent fragment.
      // This is root/branch, so there are no .../root
      //
      // This scenario may also occur if there is something wrong with the
      // path (or one of its ancestors)
      case _ => Future.successful(None)

    }

  private def modifyChildPaths(
    path: String
  ): Future[Seq[Work.Visible[Merged]]] =
    getWorksUnderPath(path) map {
      updatePaths(path, _)
    }

  private def updatePaths(
    parentPath: String,
    works: Seq[Work.Visible[Merged]]
  ): Seq[Work.Visible[Merged]] =
    works map {
      ChildWork(parentPath, _)
    }

  private def getWorksUnderPath(
    path: String
  ): Future[Seq[Work.Visible[Merged]]] = {
    if (path.isCircular) {
      Future(Nil)
    } else {
      pathsService
        .getChildWorks(path)
        .map {
          childWorks: Seq[Work.Visible[Merged]] =>
            info(s"Received ${childWorks.size} children under $path")
            childWorks.filterNot(_.data.collectionPath.get.path.isCircular)
        }
    }
  }

}
