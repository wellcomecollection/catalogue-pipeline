package weco.pipeline.sierra_indexer.services

import com.sksamuel.elastic4s.requests.delete.DeleteByQueryRequest
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import grizzled.slf4j.Logging
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.pipeline.sierra_indexer.models.{IndexerRequest, Parent}

import scala.concurrent.{ExecutionContext, Future}

// This object splits a SierraTransformable into indexable pieces
// that can be sent to Elasticsearch.
class Splitter(indexPrefix: String)(implicit ec: ExecutionContext)
    extends Logging {
  def split(
    t: SierraTransformable
  ): Future[(Seq[IndexRequest], Seq[DeleteByQueryRequest])] = Future {
    val apiData = getSierraApiData(t)

    val mainRecords = IndexerRequest.mainRecords(indexPrefix, apiData)
    val varFields = IndexerRequest.varFields(indexPrefix, apiData)
    val fixedFields = IndexerRequest.fixedFields(indexPrefix, apiData)

    val varFieldDeletions =
      IndexerRequest.varFieldDeletions(indexPrefix, apiData)
    val fixedFieldDeletions =
      IndexerRequest.fixedFieldDeletions(indexPrefix, apiData)

    (
      mainRecords ++ varFields ++ fixedFields,
      varFieldDeletions ++ fixedFieldDeletions
    )
  }

  private def getSierraApiData(t: SierraTransformable): Seq[(Parent, Json)] = {
    val itemIds = t.itemRecords.keys.map { _.withoutCheckDigit }.toList.sorted
    val holdingsIds =
      t.holdingsRecords.keys.map { _.withoutCheckDigit }.toList.sorted
    val orderIds =
      t.orderRecords.keys.map { _.withoutCheckDigit }.toList.sorted

    val bibData = t.maybeBibRecord match {
      case Some(bibRecord) =>
        Seq(
          Parent(bibRecord.id) ->
            parse(bibRecord.data)
              .map {
                json =>
                  json
                    .mapObject(
                      _.add(
                        "itemIds",
                        Json.fromValues(itemIds.map {
                          Json.fromString
                        })
                      )
                    )
                    .mapObject(
                      _.add(
                        "holdingsIds",
                        Json.fromValues(holdingsIds.map {
                          Json.fromString
                        })
                      )
                    )
                    .mapObject(
                      _.add(
                        "orderIds",
                        Json.fromValues(orderIds.map {
                          Json.fromString
                        })
                      )
                    )
              }
        )
      case None => Seq()
    }

    val itemData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.itemRecords.values.map {
        itemRecord =>
          Parent(itemRecord.id) -> parse(itemRecord.data)
      }.toSeq

    val holdingsData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.holdingsRecords.values.map {
        holdingsRecord =>
          Parent(holdingsRecord.id) -> parse(holdingsRecord.data)
      }.toSeq

    val orderData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.orderRecords.values.map {
        orderRecord =>
          Parent(orderRecord.id) -> parse(orderRecord.data)
      }.toSeq

    val data = bibData ++ itemData ++ holdingsData ++ orderData

    val successes = data.collect {
      case (parent, Right(json)) =>
        (parent, json)
    }
    val failures = data.collect { case (parent, Left(err)) => (parent, err) }

    if (failures.isEmpty) {
      successes
    } else {
      throw new Throwable(s"Could not parse all records: $failures")
    }
  }
}
