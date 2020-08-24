package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future

trait Retriever[T] {

  /** Retrieves document with the given ID from the store
    *
    * @param id The id of the document
    * @param expectedVersion Optionally includes an expected version to check
    * @return The document
    */
  def retrieve(id: String): Future[T]
}
