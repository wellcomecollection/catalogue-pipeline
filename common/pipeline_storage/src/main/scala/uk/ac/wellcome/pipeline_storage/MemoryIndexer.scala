package uk.ac.wellcome.pipeline_storage

import scala.collection.mutable
import scala.concurrent.Future

class MemoryIndexer[T: Indexable](val index: mutable.Map[String, T] = mutable.Map.empty)
    extends Indexer[T] {

  def init(): Future[Unit] =
    Future.successful(())

  def index(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]] = {
    documents.foreach { doc =>
      index.get(indexable.id(doc)) match {
        case Some(storedDoc) if indexable.version(storedDoc) > indexable.version(doc) => ()
        case _ => index.put(indexable.id(doc), doc)
      }

    }

    Future.successful(Right(documents))
  }
}
