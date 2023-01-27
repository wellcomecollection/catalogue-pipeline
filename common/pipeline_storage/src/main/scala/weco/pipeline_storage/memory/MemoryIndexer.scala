package weco.pipeline_storage.memory

import grizzled.slf4j.Logging
import weco.pipeline_storage.{Indexable, Indexer}

import scala.collection.mutable
import scala.concurrent.Future

class MemoryIndexer[T: Indexable](
  val index: mutable.Map[String, T] = mutable.Map[String, T]()
) extends Indexer[T]
    with Logging {

  def init(): Future[Unit] =
    Future.successful(())

  def apply(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]] = synchronized {
    documents.foreach { doc =>
      index.get(indexable.id(doc)) match {
        case Some(storedDoc)
            if indexable.version(storedDoc) > indexable.version(doc) =>
          info(
            s"Skipping ${indexable.id(doc)} because already indexed item has a higher version"
          )
          ()
        case _ => index.put(indexable.id(doc), doc)
      }

    }

    Future.successful(Right(documents))
  }
}
