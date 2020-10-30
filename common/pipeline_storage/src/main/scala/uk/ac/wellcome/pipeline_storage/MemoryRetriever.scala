package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future

class MemoryRetriever[T](index: Map[String, T] = Map.empty)
    extends Retriever[T] {

  def lookupSingleId(id: String): Future[T] =
    index.get(id) match {
      case Some(t) => Future.successful(t)
      case None    => Future.failed(new RetrieverNotFoundException(id))
    }
}
