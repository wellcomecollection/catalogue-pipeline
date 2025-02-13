package weco.pipeline.id_minter

import grizzled.slf4j.Logging
import io.circe.Json
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline_storage.{Retriever, RetrieverMultiResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MultiIdMinter(jsonRetriever: Retriever[Json], minter: IdMinter)
    extends Logging {

  /** Given some identifiers, fetches their corresponding records from the
    * upstream database, mint canonicalIdentifiers for and within each document.
    * The nature of any failures are logged, and the failing ids are returned
    * @return
    *   a Seq of Eithers: Right represents the successfully minted/transformed
    *   document, Left is an id that could not be processed for some reason.
    */
  def processSourceIds(
    identifiers: Seq[String]
  )(
    implicit ec: ExecutionContext
  ): Future[Iterable[Either[String, Work[Identified]]]] = {

    // run through the happy path (logging, but otherwise ignoring exceptions as we go)
    val futureSuccesses = jsonRetriever(identifiers)
      .map {
        result: RetrieverMultiResult[Json] =>
          // At this point, it would be possible to collect the failed identifiers,
          // but only for the retrieval step.  We have to do filter-not-successful for the
          // fail-to-mint step, which would also cover these, so there's no point.
          result.notFound.values.foreach {
            exception => error(exception)
          }
          result.found.values.map(minter.processJson).flatMap {
            maybeProcessed =>
              maybeProcessed match {
                case Failure(exception) =>
                  error(exception)
                  None
                case Success(value) => Some(value)
              }
          }
      }

    futureSuccesses.map {
      iterWorks =>
        // If it succeeded, it's a Right
        val seqWorks = iterWorks.map(Right(_))
        // Create a set of source identifiers from the successfully processed ones
        val successfulIds =
          seqWorks.map(_.right.get.sourceIdentifier.toString)
        // populate a Seq of Lefts with any input identifier that is not in the successful set
        val failedIds = identifiers.toSet -- successfulIds
        // bang together the Rights and the Lefts and return the lot.
        seqWorks ++ failedIds.map(Left(_))
    }

  }
}
