package uk.ac.wellcome.pipeline_storage

import scala.concurrent.Future

trait Retriever[T] {

  /** Retrieves document with the given ID from the store
    *
    * @param id The id of the document
    * @return A future containing the document
    */
  def lookupSingleId(id: String): Future[T]
}
