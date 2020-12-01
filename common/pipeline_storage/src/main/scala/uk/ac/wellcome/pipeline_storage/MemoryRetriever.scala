package uk.ac.wellcome.pipeline_storage

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class MemoryRetriever[T](val index: mutable.Map[String, T] =
                           mutable.Map[String, T]())(
  implicit val ec: ExecutionContext)
    extends Retriever[T] {

  override def apply(ids: Seq[String]): Future[Map[String, T]] =
    Future {
      ids.map { id =>
        id -> index(id)
      }.toMap
    }
}
