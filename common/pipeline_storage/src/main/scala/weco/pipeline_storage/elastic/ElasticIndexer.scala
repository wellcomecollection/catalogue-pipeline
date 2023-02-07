package weco.pipeline_storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.bulk.BulkResponseItem
import com.sksamuel.elastic4s.requests.common.VersionType.ExternalGte
import com.sksamuel.elastic4s.{
  ElasticClient,
  Index,
  Indexable => ElasticIndexable
}
import grizzled.slf4j.Logging
import io.circe.{Encoder, Printer}
import weco.elasticsearch.{ElasticsearchIndexCreator, IndexConfig}
import weco.pipeline_storage.{Indexable, Indexer}

import java.security.MessageDigest
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ElasticIndexer[T: Indexable](
  client: ElasticClient,
  index: Index,
  config: IndexConfig
)(implicit ec: ExecutionContext, encoder: Encoder[T])
    extends Indexer[T]
    with Logging {

  implicit val elasticIndexable = new ElasticIndexable[T] {
    override def json(item: T): String = {
      // Here we drop nulls in the indexed json to reduce storage size: this is
      // mainly for relations which only contain very limited work data.
      // In circe v0.14.0 we may also consider adding dropEmptyValues, which is
      // a new Json method that is non trivial to include here
      val json = encoder(item).deepDropNullValues
      Printer.noSpaces.print(json)
    }
  }

  final def init(): Future[Unit] =
    new ElasticsearchIndexCreator(client, index, config).create

  final def apply(documents: Seq[T]): Future[Either[Seq[T], Seq[T]]] =
    Future
      .fromTry(
        Try(
          require(documents.nonEmpty, "Cannot index an empty list of documents")
        )
      )
      .flatMap {
        _ =>
          debug(
            s"Indexing ${documents.map(doc => indexable.id(doc)).mkString(", ")}"
          )
          val inserts = documents.map {
            document =>
              indexInto(index.name)
                .version(indexable.version(document))
                .versionType(ExternalGte)
                .id(indexable.id(document))
                .doc(document)
          }

          client
            .execute {
              bulk(inserts)
            }
            .flatMap {

              // An HTTP 413 Request Too Large error means we tried to index too
              // many documents at once.  In this case, we split the list in half
              // and try to index both halves separately.
              //
              // Either we'll narrow it down to a single document that's to big to be
              // indexed, or we'll index everything successfully.
              case response if response.status == 413 =>
                // If there's only one document left, there's nothing to do --
                // this document is Just Too Big.
                // https://twitter.com/smolrobots/status/1001226918107246592
                if (documents.size == 1) {
                  warn(s"HTTP 413 from Elasticsearch for a single document (${indexable
                      .id(documents.head)})")
                  Future.successful(Left(documents))
                }

                // Slice the documents in two, and index them both separately.
                // We'll combine the results.
                //
                // The "trace ID" allows us to follow multiple requests through the logs.
                // It's a hashed version of all the IDs in each slice, which should be
                // easier to follow than dumping all the IDs to the logs.
                else {
                  val (slice0, slice1) = documents.splitAt(documents.size / 2)

                  val traceId =
                    s"${createTraceId(documents)} => ${createTraceId(slice0)} / ${createTraceId(slice1)}}"
                  warn(
                    s"HTTP 413 from Elasticsearch (${documents.size} documents); trying smaller slices (trace $traceId)"
                  )

                  val futures: Future[Seq[Either[Seq[T], Seq[T]]]] =
                    Future
                      .sequence(Seq(apply(slice0), apply(slice1)))
                      .map {
                        result =>
                          info(
                            s"Received both results from HTTP 413 retry (trace $traceId)"
                          )
                          result
                      }

                  futures.map {
                    case Seq(Right(docs0), Right(docs1)) =>
                      Right(docs0 ++ docs1)
                    case Seq(Left(docs0), Left(docs1)) => Left(docs0 ++ docs1)
                    case Seq(Left(docs0), _)           => Left(docs0)
                    case Seq(_, Left(docs1))           => Left(docs1)
                  }
                }

              case response if response.isError =>
                error(s"Error from Elasticsearch: $response")
                Future.successful(Left(documents))

              case response =>
                debug(s"Bulk response = $response")
                val bulkResponse = response.result
                val actualFailures = bulkResponse.failures.filterNot {
                  isVersionConflictException
                }

                if (actualFailures.nonEmpty) {
                  val failedIds = actualFailures.map {
                    failure =>
                      error(s"Failed ingesting ${failure.id}: ${failure.error}")
                      failure.id
                  }

                  Future.successful(
                    Left(
                      documents.filter(
                        doc => {
                          failedIds.contains(indexable.id(doc))
                        }
                      )
                    )
                  )
                } else Future.successful(Right(documents))
            }
      }

  /** Did we try to PUT a document with a lower version than the existing
    * version?
    */
  private def isVersionConflictException(
    bulkResponseItem: BulkResponseItem
  ): Boolean = {
    // This error is returned by Elasticsearch when we try to PUT a document
    // with a lower version than the existing version.
    val alreadyIndexedHasHigherVersion = bulkResponseItem.error
      .exists(
        bulkError =>
          bulkError.`type`.contains("version_conflict_engine_exception")
      )

    if (alreadyIndexedHasHigherVersion) {
      info(
        s"Skipping ${bulkResponseItem.id} because already indexed item has a higher version (${bulkResponseItem.error}"
      )
    }

    alreadyIndexedHasHigherVersion
  }

  private def createTraceId(documents: Seq[T]): String = {
    val concatenatedIds =
      documents.map { indexable.id }.sorted.mkString("::")

    MessageDigest
      .getInstance("MD5")
      .digest(concatenatedIds.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString
  }
}
