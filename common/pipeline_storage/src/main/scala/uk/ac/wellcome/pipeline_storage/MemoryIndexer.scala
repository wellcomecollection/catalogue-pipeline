package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future
import scala.collection.mutable.Map

class MemoryIndexer[T: Indexable](val index: Map[String, T] = Map.empty)
    extends Indexer[T] {

  def init(): Future[Unit] =
    Future.successful(())

  def index(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]] = {
    documents.map(doc => index.put(indexable.id(doc), doc))
    Future.successful(Right(documents))
  }
}
