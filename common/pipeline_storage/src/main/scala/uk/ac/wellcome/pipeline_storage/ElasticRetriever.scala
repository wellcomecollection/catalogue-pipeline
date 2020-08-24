package uk.ac.wellcome.pipeline_storage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}

import com.sksamuel.elastic4s.{ElasticClient, Index, RequestSuccess, RequestFailure}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import io.circe.Decoder
import grizzled.slf4j.Logging

class ElasticRetriever[T](client: ElasticClient, index: Index)(
  implicit ec: ExecutionContext,
  decoder: Decoder[T])
    extends Retriever[T]
    with Logging {

  final def retrieve(id: String): Future[T] =
    client
      .execute {
        get(index, id)
      }
      .flatMap {
        case RequestFailure(_, _, _, error) => Future.failed(error.asException)
        case RequestSuccess(_, _, _, response) =>
          response.safeTo[T] match {
            case Success(item) => Future.successful(item)
            case Failure(error) => Future.failed(error)
          }
      }
}
