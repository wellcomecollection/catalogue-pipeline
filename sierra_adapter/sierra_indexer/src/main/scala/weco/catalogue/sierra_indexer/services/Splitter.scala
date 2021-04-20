package weco.catalogue.sierra_indexer.services

import com.sksamuel.elastic4s.{ElasticClient, Index}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.delete.DeleteByQueryRequest
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import grizzled.slf4j.Logging
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import weco.catalogue.sierra_indexer.models.{IndexerRequest, Parent}
import weco.catalogue.source_model.sierra.SierraTransformable

import scala.concurrent.{ExecutionContext, Future}

// This object splits a SierraTransformable into indexable pieces
// that can be sent to Elasticsearch.
class Splitter(indexPrefix: String)(
  implicit
  client: ElasticClient,
  ec: ExecutionContext
) extends Logging {
  import weco.catalogue.sierra_indexer.services.SierraJsonOps._

  def split(t: SierraTransformable): Future[(Seq[IndexRequest], Seq[DeleteByQueryRequest])] = {
    for {
      apiData <- getSierraApiData(t)

      // This is an attempt to reduce the pressure on the Elasticsearch cluster.
      //
      // We look at the main record for this bib/item/holdings, and compare it to what's
      // already stored.  Note that these records include the modifiedDate/deletedDate, e.g
      //
      //    {"id": "1234567", "modifiedDate": "2001-01-01T01:01:01Z", …}
      //
      // and we assume that this value will change when the record is modified.
      // Because the modified/deletedDate is part of the JSON, it is sufficient to compare
      // the JSON strings without extracting the dates directly.
      //
      // If you wanted to go one step further, you could diff individual
      // varFields/fixedFields, but that adds more complexity.
      //
      // I'm hoping this is a quick fix that makes the indexer performance "good enough"
      // without us having to pour money into the reporting cluster.
      stored <- getStored(apiData)
      modifiedApiData =
        stored
          .filter {
            case ((parent, json), getResponse) if jsonStringsMatch(json.withId(parent.id).remainder, getResponse.sourceAsString) =>
              debug(s"Not indexing ${parent.id.withCheckDigit}; it is already indexed")
              false

            case ((parent, _), _) =>
              debug(s"Indexing ${parent.id.withCheckDigit}")
              true
          }
          .map { case ((parent, json), _) => (parent, json) }

      mainRecords = IndexerRequest.mainRecords(indexPrefix, modifiedApiData)
      varFields = IndexerRequest.varFields(indexPrefix, modifiedApiData)
      fixedFields = IndexerRequest.fixedFields(indexPrefix, modifiedApiData)

      varFieldDeletions = IndexerRequest.varFieldDeletions(indexPrefix, modifiedApiData)
      fixedFieldDeletions = IndexerRequest.fixedFieldDeletions(
        indexPrefix,
        modifiedApiData)
    } yield
      (
        mainRecords ++ varFields ++ fixedFields,
        varFieldDeletions ++ fixedFieldDeletions
      )
  }

  private def jsonStringsMatch(j1: Json, j2: String): Boolean =
    parse(j2) match {
      case Right(value) => value == j1
      case _ => false
    }

  private def getStored(apiData: Seq[(Parent, Json)]): Future[Seq[((Parent, Json), GetResponse)]] = {
    val gets = apiData.map { case (parent, _) =>
      get(Index(s"${indexPrefix}_${parent.recordType}"), id = parent.id.withoutCheckDigit)
    }

    client.execute(multiget(gets)).map { resp =>
      apiData.zip(resp.result.items)
    }
  }

  private def getSierraApiData(t: SierraTransformable): Future[Seq[(Parent, Json)]] = {
    val itemIds = t.itemRecords.keys.map { _.withoutCheckDigit }.toList.sorted
    val holdingsIds =
      t.holdingsRecords.keys.map { _.withoutCheckDigit }.toList.sorted

    val bibData = t.maybeBibRecord match {
      case Some(bibRecord) =>
        Seq(
          Parent(bibRecord.id) ->
            parse(bibRecord.data)
              .map { json =>
                json
                  .mapObject(_.add("itemIds", Json.fromValues(itemIds.map {
                    Json.fromString
                  })))
                  .mapObject(
                    _.add("holdingsIds", Json.fromValues(holdingsIds.map {
                      Json.fromString
                    })))
              }
        )
      case None => Seq()
    }

    val itemData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.itemRecords.values.map { itemRecord =>
        Parent(itemRecord.id) -> parse(itemRecord.data)
      }.toSeq

    val holdingsData: Seq[(Parent, Either[ParsingFailure, Json])] =
      t.holdingsRecords.values.map { holdingsRecord =>
        Parent(holdingsRecord.id) -> parse(holdingsRecord.data)
      }.toSeq

    val data = bibData ++ itemData ++ holdingsData

    val successes = data.collect {
      case (parent, Right(json)) => (parent, json)
    }
    val failures = data.collect { case (parent, Left(err)) => (parent, err) }

    if (failures.isEmpty) {
      Future.successful(successes)
    } else {
      Future.failed(new Throwable(s"Could not parse all records: $failures"))
    }
  }
}
