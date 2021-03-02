package weco.catalogue.sierra_indexer.services

import com.sksamuel.elastic4s.requests.delete.DeleteByQueryRequest
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import weco.catalogue.sierra_adapter.models.{
  SierraRecordTypes,
  SierraTransformable
}
import weco.catalogue.sierra_indexer.models.{IndexerRequest, Parent}

// This object splits a SierraTransformable into indexable pieces
// that can be sent to Elasticsearch.
class Splitter(indexPrefix: String) {
  def split(t: SierraTransformable)
    : Either[Seq[(Parent, ParsingFailure)],
             (Seq[IndexRequest], Seq[DeleteByQueryRequest])] = {
    for {
      apiData <- getSierraApiData(t)

      mainRecords = IndexerRequest.mainRecords(indexPrefix, apiData)
      varFields = IndexerRequest.varFields(indexPrefix, apiData)
      fixedFields = IndexerRequest.fixedFields(indexPrefix, apiData)

      varFieldDeletions = IndexerRequest.varFieldDeletions(indexPrefix, apiData)
      fixedFieldDeletions = IndexerRequest.fixedFieldDeletions(
        indexPrefix,
        apiData)
    } yield
      (
        mainRecords ++ varFields ++ fixedFields,
        varFieldDeletions ++ fixedFieldDeletions
      )
  }

  private def getSierraApiData(t: SierraTransformable)
    : Either[Seq[(Parent, ParsingFailure)], Seq[(Parent, Json)]] = {
    val bibData = t.maybeBibRecord match {
      case Some(bibRecord) =>
        Seq(
          Parent(SierraRecordTypes.bibs, bibRecord.id) -> parse(bibRecord.data)
        )
      case None => Seq()
    }

    val itemData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.itemRecords.values.map { itemRecord =>
        Parent(SierraRecordTypes.items, itemRecord.id) -> parse(itemRecord.data)
      }.toSeq

    val holdingsData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.holdingsRecords.values.map { holdingsRecord =>
        Parent(SierraRecordTypes.holdings, holdingsRecord.id) -> parse(
          holdingsRecord.data)
      }.toSeq

    val data = bibData ++ itemData ++ holdingsData

    val successes = data.collect {
      case (parent, Right(json)) => (parent, json)
    }
    val failures = data.collect { case (parent, Left(err)) => (parent, err) }

    Either.cond(failures.isEmpty, successes, failures)
  }
}
