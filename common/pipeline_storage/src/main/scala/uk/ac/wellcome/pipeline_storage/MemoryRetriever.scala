package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

class MemoryRetriever[T](index: Map[String, T] = Map.empty)(implicit val ec: ExecutionContext)
    extends Retriever[T] {

  def apply(id: String): Future[T] =
    index.get(id) match {
      case Some(t) => Future.successful(t)
      case None    => Future.failed(new RetrieverNotFoundException(id))
    }
}
