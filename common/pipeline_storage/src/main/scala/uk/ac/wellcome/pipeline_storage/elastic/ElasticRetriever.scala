package uk.ac.wellcome.pipeline_storage.elastic

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import com.sksamuel.elastic4s.requests.get.{GetRequest, GetResponse}
import com.sksamuel.elastic4s.{
  ElasticClient,
  Index,
  RequestFailure,
  RequestSuccess
}
import com.sksamuel.elastic4s.ElasticDsl._
import grizzled.slf4j.Logging
import uk.ac.wellcome.pipeline_storage.{
  Retriever,
  RetrieverMultiResult,
  RetrieverNotFoundException
}

trait ElasticRetriever[T] extends Retriever[T] with Logging {
  val client: ElasticClient
  val index: Index

  def createGetRequest(id: String): GetRequest
  def parseGetResponse(response: GetResponse): Try[T]

  override final def apply(ids: Seq[String]): Future[RetrieverMultiResult[T]] =
    for {
      _ <- Future.fromTry(
        Try(
          require(
            ids.nonEmpty,
            "You should never look up an empty list of IDs!"
          )))

      result <- client
        .execute {
          multiget(
            ids.map { createGetRequest }
          )
        }
        .map {
          case RequestFailure(_, _, _, error) => throw error.asException
          case RequestSuccess(_, _, _, result)
              if result.docs.size != ids.size =>
            warn(
              s"Asked for ${ids.size} IDs in index $index, only got ${result.docs.size}")
            throw new RetrieverNotFoundException(ids.mkString(", "))
          case RequestSuccess(_, _, _, result) =>
            // Documents are guaranteed to be returned in the same order as the
            // original IDs.
            // See https://www.elastic.co/guide/en/elasticsearch/reference/6.8/docs-multi-get.html
            val documents = result.docs
              .zip(ids)
              .map {
                case (getResponse, id) =>
                  if (getResponse.found) {
                    id -> parseGetResponse(getResponse)
                  } else {
                    id -> Failure(
                      new RetrieverNotFoundException(
                        id,
                        Some(getResponse.sourceAsString)))
                  }
              }
              .toMap

            RetrieverMultiResult(
              found = documents.collect { case (id, Success(t))    => id -> t },
              notFound = documents.collect { case (id, Failure(e)) => id -> e }
            )
        }
    } yield result
}
