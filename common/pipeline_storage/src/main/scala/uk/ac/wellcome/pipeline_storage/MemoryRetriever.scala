package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

class MemoryRetriever[T](var index: Map[String, T] = Map.empty)(
  implicit val ec: ExecutionContext)
    extends Retriever[T] {

  override def apply(ids: Seq[String]): Future[Map[String, T]] =
    Future {
      ids.map { id =>
        id -> index(id)
      }.toMap
    }
}
