package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.sksamuel.elastic4s.{
  ElasticClient,
  Index,
  RequestFailure,
  RequestSuccess
}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import io.circe.Decoder
import grizzled.slf4j.Logging

class ElasticRetriever[T](client: ElasticClient, index: Index)(
  implicit val ec: ExecutionContext,
  decoder: Decoder[T])
    extends Retriever[T]
    with Logging {

  override final def apply(ids: Seq[String]): Future[RetrieverMultiResult[T]] =
    client
      .execute {
        multiget(
          ids.map { id =>
            get(index, id)
          }
        )
      }
      .map {
        case RequestFailure(_, _, _, error) => throw error.asException
        case RequestSuccess(_, _, _, result) if result.docs.size != ids.size =>
          warn(
            s"Asked for ${ids.size} IDs in index $index, only got ${result.docs.size}")
          throw new RetrieverNotFoundException(ids.mkString(", "))
        case RequestSuccess(_, _, _, result) =>
          // Documents are guaranteed to be returned in the same order as the
          // original IDs.
          // See https://www.elastic.co/guide/en/elasticsearch/reference/6.8/docs-multi-get.html
          val documents = result.docs
            .zip(ids)
            .map { case (getResponse, id) =>
              if (getResponse.found) {
                id -> getResponse.safeTo[T]
              } else {
                id -> Failure(new RetrieverNotFoundException(id))
              }
            }
            .toMap

          RetrieverMultiResult(
            found = documents.collect { case (id, Success(t)) => id -> t },
            notFound = documents.collect { case (id, Failure(e)) => id -> e }
          )
      }
}
