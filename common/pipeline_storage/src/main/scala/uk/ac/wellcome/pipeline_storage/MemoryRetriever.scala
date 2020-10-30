package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

class MemoryRetriever[T](index: Map[String, T] = Map.empty)(implicit ec: ExecutionContext)
    extends Retriever[T] {

  override def lookupSingleId(id: String): Future[T] =
    index.get(id) match {
      case Some(t) => Future.successful(t)
      case None    => Future.failed(new RetrieverNotFoundException(id))
    }

  override def lookupMultipleIds(ids: Seq[String]): Future[Map[String, T]] =
    Future.sequence(ids.map { lookupSingleId })
      .map { documents => ids.zip(documents).toMap }
}
