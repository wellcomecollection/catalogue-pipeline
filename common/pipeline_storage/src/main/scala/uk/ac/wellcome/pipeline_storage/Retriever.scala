package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

trait Retriever[T] {

  implicit val ec: ExecutionContext

  /** Retrieves document with the given ID from the store
    *
    * @param id The id of the document
    * @return A future containing the document
    */
  def apply(id: String): Future[T]

  /** Retrieves a series of documents from the store. */
  def apply(ids: Seq[String]): Future[Map[String, T]] =
    Future
      .sequence(ids.map { apply })
      .map { documents => ids.zip(documents).toMap }
}
