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

  final def lookupSingleId(id: String): Future[T] = {
    client
      .execute {
        get(index, id)
      }
      .flatMap {
        case RequestFailure(_, _, _, error) => Future.failed(error.asException)
        case RequestSuccess(_, _, _, response) if !response.found =>
          warn(
            s"Asked to look up ID $id in index $index, got response $response")
          Future.failed(RetrieverNotFoundException.id(id))
        case RequestSuccess(_, _, _, response) =>
          response.safeTo[T] match {
            case Success(item)  => Future.successful(item)
            case Failure(error) => Future.failed(error)
          }
      }
  }

  override def lookupMultipleIds(ids: Seq[String]): Future[Map[String, T]] = {
    client
      .execute {
        multiget(ids.map { get(index, _) })
      }
      .flatMap {
        case RequestSuccess(_, _, _, response) =>
          val responses = ids.zip(response.docs).toMap

          val missing = responses.filter { case (_, resp) => !resp.found }
          val present = responses.filter { case (_, resp) => resp.found }

          val decodedT = present.map { case (id, resp)             => id -> resp.safeTo[T] }
          val successes = decodedT.collect { case (id, Success(s)) => id -> s }
          val failures = decodedT.collect {
            case (id, Failure(err)) => id -> err
          }

          assert(
            successes.size + failures.size + missing.size == responses.size)

          if (missing.nonEmpty) {
            Future.failed(RetrieverNotFoundException.ids(missing.keys.toSeq))
          } else if (failures.nonEmpty) {
            Future.failed(
              new RuntimeException(
                s"Unable to decode some responses: $failures"))
          } else {
            Future.successful(successes)
          }
        case _ =>
          Future.failed(new RuntimeException("BOOM!"))
      }
  }
}
