package uk.ac.wellcome.pipeline_storage

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class MemoryRetriever[T](val index: mutable.Map[String, T] =
                           mutable.Map[String, T]())(
  implicit val ec: ExecutionContext)
    extends Retriever[T] {

  override def apply(ids: Seq[String]): Future[RetrieverMultiResult[T]] =
    Future {
      val lookupResults =
        ids.map { id =>
          id -> index.get(id)
        }.toMap

      RetrieverMultiResult(
        found = lookupResults.collect { case (id, Some(t)) => (id, t) },
        notFound = lookupResults.collect {
          case (id, None) => (id, new RetrieverNotFoundException(id))
        }
      )
    }
}
