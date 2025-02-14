package weco.pipeline.id_minter

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline_storage.Indexer

import scala.concurrent.{ExecutionContext, Future}

class MintingRequestProcessor(
  minter: IdListMinter,
  workIndexer: Indexer[Work[Identified]]
)(implicit ec: ExecutionContext) {

  def process(ids: Seq[String]): Future[Seq[String]] =
    minter.processSourceIds(ids) flatMap {
      results =>
        val (successes, mintingFailures) = results.partition(_.isRight)
        val storageResults = workIndexer(successes.map(_.right.get).toSeq)
        storageResults.map {
          case Left(storageFailures) =>
            storageFailures.map(_.sourceIdentifier.toString)
          case Right(_) => mintingFailures.map(_.left.get).toSeq
        }
    }
}
