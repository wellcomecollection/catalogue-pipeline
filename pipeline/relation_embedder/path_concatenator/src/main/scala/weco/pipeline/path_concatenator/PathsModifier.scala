package weco.pipeline.path_concatenator

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Merged
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

/**
  *  Given a path, Fetch and modify the relevant Works (if necessary)
  *  - Modify the work with that exact path.
  *  - Modify all works under that exact path.
  *
  *  To do this, it needs to also find the path of the record representing
  *  the first node in this path.
  *
  *  So, given records with paths:
  *  - a
  *  - a/b
  *  - c/d
  *  - c/e
  *  - b/c
  *
  *  When RecordModifier encounters b/c, it will
  *  - fetch the path a/b
  *  - change b/c to a/b/c
  *  - change c/d and c/e to a/b/c/d and a/b/c/e, respectively,
  */
case class PathsModifier(pathsService: PathsService)(
  implicit ec: ExecutionContext,
  materializer: Materializer)
    extends Logging {

  def modifyPaths(path: String): Future[Seq[Work.Visible[Merged]]] = {
    modifyCurrentPath(path) flatMap {
      case None               => modifyChildPaths(path)
      case Some(modifiedWork) =>
        //val newPath: String = modifiedWork.data.collectionPath.get.path
        modifyChildPaths(modifiedWork.data.collectionPath.get.path) flatMap {
          childWorks: Seq[Work.Visible[Merged]] =>
            Future(childWorks :+ modifiedWork)
        }
    }
    //TODO save the changes (in the caller?)
  }

  def modifyCurrentPath(path: String): Future[Option[Work.Visible[Merged]]] =
    getParentPath(path) flatMap {
      case Some(parentPath) =>
        getWorkWithPath(path) flatMap { work =>
          Future(Some(ChildWork(parentPath, work)))
        }
      case _ => Future.successful(None) // This is expected, if parent is root

    }

  def modifyChildPaths(path: String): Future[Seq[Work.Visible[Merged]]] =
    getWorksUnderPath(path) flatMap { works: Seq[Work.Visible[Merged]] =>
      Future(updatePaths(path, works))
    }

  def updatePaths(parentPath: String,
                  works: Seq[Work.Visible[Merged]]): Seq[Work.Visible[Merged]] =
    works map { work: Work.Visible[Merged] =>
      ChildWork(parentPath, work)
    }

  def getParentPath(path: String): Future[Option[String]] = {
    pathsService
      .getParentPath(path)
      .runWith(Sink.seq[String])
      .map {
        // Only return the head if the list has exactly one path in it.
        // If there are more, then there is a data error.
        case Nil => None
        case Seq(parentPath) => Some(parentPath)
        case multiplePaths =>
        throw new RuntimeException( s"$path has more than one possible parents: $multiplePaths" )
      }
  }

  def getWorkWithPath(path: String): Future[Work.Visible[Merged]] = {
    pathsService
      .getWorkWithPath(path)
      .runWith(Sink.seq[Work.Visible[Merged]])
      .map {
        case Nil => throw new RuntimeException( s"No work found with path: $path")
        case Seq(currentWork) => currentWork
        case multipleWorks =>
          throw new RuntimeException( s"$path has more than one possible works: $multipleWorks" )
      }
  }

  def getWorksUnderPath(path: String): Future[Seq[Work.Visible[Merged]]] = {
    pathsService
      .getChildWorks(path)
      .runWith(Sink.seq[Work.Visible[Merged]])
      .map { childWorks =>
        info(s"Received ${childWorks.size} children")
        childWorks
      }
  }

}
