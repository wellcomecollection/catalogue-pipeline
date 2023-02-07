package weco.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}

trait Retriever[T] {

  implicit val ec: ExecutionContext

  /** Retrieves document with the given ID from the store
    *
    * @param id
    *   The id of the document
    * @return
    *   A future containing the document
    */
  def apply(id: String): Future[T] =
    apply(Seq(id))
      .map {
        result =>
          result.found.getOrElse(
            id,
            throw result.notFound(id)
          )
      }

  /** Retrieves a series of documents from the store. */
  def apply(ids: Seq[String]): Future[RetrieverMultiResult[T]]
}
