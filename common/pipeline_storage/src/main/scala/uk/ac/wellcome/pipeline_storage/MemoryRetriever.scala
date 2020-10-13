package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future
import scala.util.Try

class MemoryRetriever[T](index: Map[String, T] = Map.empty)
    extends Retriever[T] {

  def apply(id: String): Future[T] =
    Future.fromTry(Try(index(id)))
}
