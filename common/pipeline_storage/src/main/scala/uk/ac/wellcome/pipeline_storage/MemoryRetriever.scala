package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future

class MemoryRetriever[T](index: Map[String, T] = Map.empty)
    extends Retriever[T] {

  override def lookupSingleId(id: String): Future[T] =
    index.get(id) match {
      case Some(t) => Future.successful(t)
      case None    => Future.failed(RetrieverNotFoundException.id(id))
    }

  override def lookupMultipleIds(ids: Seq[String]): Future[Map[String, T]] = {
    val results = ids.map { id =>
      id -> index.get(id)
    }

    val successes = results.collect { case (id, Some(t)) => id -> t }
    val failures = results.collect { case (id, None)     => id }

    if (failures.isEmpty) {
      Future.successful(successes.toMap)
    } else {
      Future.failed(RetrieverNotFoundException.ids(failures))
    }
  }
}
