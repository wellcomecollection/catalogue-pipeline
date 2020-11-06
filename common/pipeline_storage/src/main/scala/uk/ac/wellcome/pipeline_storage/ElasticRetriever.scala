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
    debug(s"Looking up ID $id in index $index")
    client
      .execute {
        get(index, id)
      }
      .flatMap {
        case RequestFailure(_, _, _, error) => Future.failed(error.asException)
        case RequestSuccess(_, _, _, response) if !response.found =>
          warn(
            s"Asked to look up ID $id in index $index, got response $response")
          Future.failed(new RetrieverNotFoundException(id))
        case RequestSuccess(_, _, _, response) =>
          response.safeTo[T] match {
            case Success(item)  => Future.successful(item)
            case Failure(error) => Future.failed(error)
          }
      }
  }
}
