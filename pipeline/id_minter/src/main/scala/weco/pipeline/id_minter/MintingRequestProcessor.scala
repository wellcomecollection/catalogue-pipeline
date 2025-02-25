package weco.pipeline.id_minter

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline_storage.Indexer

import scala.concurrent.{ExecutionContext, Future}

case class MintingResponse(successes: Seq[String], failures: Seq[String])

/** Handles the processing of a Minting request.
  *
  * A minting request consists of a sequence of Source Identifiers pointing to
  * records in an upstream database.
  *
  * The request is processed by
  *   - fetching those upstream records,
  *   - minting new identifiers for them all
  *   - storing the modified records downstream
  *
  * @param minter
  *   Fetches the records from upstream and mints new identifiers
  * @param workIndexer
  *   Places the modified records in the downstream database
  */
class MintingRequestProcessor(
  minter: IdListMinter,
  workIndexer: Indexer[Work[Identified]]
)(implicit ec: ExecutionContext) extends Logging {

  def process(sourceIdentifiers: Seq[String]): Future[MintingResponse] = {
    sourceIdentifiers match {
      case Nil => Future.successful(MintingResponse(Nil, Nil))
      case ids =>
        minter.processSourceIds(ids) flatMap {
          results =>
            val (mintingSuccesses, mintingFailures) =
              results.partition(_.isRight)
            val successfullyMintedDocuments =
              mintingSuccesses.map(_.right.get).toSeq
            val storageResults =
              workIndexer(successfullyMintedDocuments)
            // Indexer responses are all-or-nothing.
            // If all records are stored successfully, then a Right is returned,
            // containing all the successful records.
            // However, if _any_ records fail, then a Left is returned containing
            // all the failed records.
            storageResults.map {
              case Left(storageFailures) =>
                storageFailures.map(
                  _.sourceIdentifier.toString
                ) ++ mintingFailures
                  .map(_.left.get)
                  .toSeq
              case Right(successes) =>
                info(s"Successfully stored ${successes.size}/${ids.size} records: ${successes.map(_.id).mkString(", ")}")
                mintingFailures.map(_.left.get).toSeq
            } map {
              failures =>
                // Indexer is not guaranteed to return all successful documents.
                // However, it will tell us about all failures.
                // In order to work out which records were successfully stored, we need to take a
                // "did not fail" approach.
                val successes = successfullyMintedDocuments
                  .filter(
                    doc => !failures.contains(doc.sourceIdentifier.toString)
                  )
                  .map(_.id)
                MintingResponse(successes = successes, failures = failures)
            }
        }
    }
  }
}
