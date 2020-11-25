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
  implicit ec: ExecutionContext,
  decoder: Decoder[T])
    extends Retriever[T]
    with Logging {

  final def apply(id: String): Future[T] = {
    client
      .execute {
        get(index, id)
      }
      .map {
        case RequestFailure(_, _, _, error) => throw error.asException
        case RequestSuccess(_, _, _, response) if !response.found =>
          warn(
            s"Asked to look up ID $id in index $index, got response $response")
          throw new RetrieverNotFoundException(id)
        case RequestSuccess(_, _, _, response) =>
          response.safeTo[T] match {
            case Success(item)  => item
            case Failure(error) => throw error
          }
      }
  }
}
