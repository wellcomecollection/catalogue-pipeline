package weco.pipeline_storage.elastic

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
import weco.pipeline_storage.{
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
          )
        )
      )

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
              s"Asked for ${ids.size} IDs in index $index, only got ${result.docs.size}"
            )
            throw new RetrieverNotFoundException(ids.mkString(", "))
          case RequestSuccess(_, _, _, result) =>
            // Documents are guaranteed to be returned in the same order as the
            // original IDs.
            // See https://www.elastic.co/guide/en/elasticsearch/reference/6.8/docs-multi-get.html
            val documents = result.docs
              .zip(ids)
              .map { case (getResponse, id) =>
                if (getResponse.found) {
                  id -> parseGetResponse(getResponse)
                } else {
                  id -> Failure(
                    new RetrieverNotFoundException(
                      id,
                      Some(getResponse.sourceAsString)
                    )
                  )
                }
              }
              .toMap

            RetrieverMultiResult(
              found = documents.collect { case (id, Success(t)) => id -> t },
              notFound = documents.collect { case (id, Failure(e)) => id -> e }
            )
        }
        .recoverWith {
          // If we try to retrieve too large a set of documents in one go, we can throw
          // an OutOfMemory error here.
          //
          // This bubbles up to SQSStream as an org.apache.http.ConnectionClosedException,
          // which isn't necessarily terminal -- but running out of memory and losing
          // the Akka actor is.  The app will no longer process messages because Akka has
          // stopped, so there's nothing more useful we can do.
          //
          // Stopping the application will cause it to be restarted, and hopefully the
          // next set of messages won't throw this error.
          case e: OutOfMemoryError =>
            error(
              s"Out of memory error thrown while trying to retrieve ${ids.size} IDs: $ids",
              e
            )
            System.exit(1)

            // This line is just to keep the compiler happy, because it can't see that
            // it's unhittable.
            Future.failed(e)
        }
    } yield result
}
