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
case class PathsModifier(pathsService: PathsService)(implicit
  ec: ExecutionContext
) extends Logging {

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
      case Some(parentPath) =>
        pathsService.getWorkWithPath(path) map { work: Work.Visible[Merged] =>
          Some(ChildWork(parentPath, work))
        }
      case _ => Future.successful(None) // This is expected, if parent is root

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
    pathsService
      .getChildWorks(path)
      .map { childWorks =>
        info(s"Received ${childWorks.size} children under $path")
        childWorks
      }
  }

}
