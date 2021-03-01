package weco.catalogue.sierra_indexer.services

import com.sksamuel.elastic4s.ElasticApi.{
  must,
  rangeQuery,
  termQuery,
  termsQuery
}
import com.sksamuel.elastic4s.{Index, Indexes}
import com.sksamuel.elastic4s.requests.delete.DeleteByQueryRequest
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json, ParsingFailure}
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.sierra_adapter.models.{
  SierraRecordTypes,
  SierraTransformable,
  TypedSierraRecordNumber
}
import weco.catalogue.sierra_indexer.models.Parent

// This object splits a SierraTransformable into indexable pieces
// that can be sent to Elasticsearch.
class Splitter(indexPrefix: String) {
  import JsonOps._

  private val varFieldIndex = Index(s"${indexPrefix}_varfields")
  private val fixedFieldsIndex = Index(s"${indexPrefix}_fixedfields")

  implicit val encoder: Encoder[SierraRecordTypes.Value] =
    (value: SierraRecordTypes.Value) => Json.fromString(value.toString)

  implicit val encoderNumber: Encoder[TypedSierraRecordNumber] =
    (number: TypedSierraRecordNumber) => Json.fromString(number.withoutCheckDigit)

  implicit val encoderParent: Encoder[Parent] = deriveConfiguredEncoder

  private case class IndexedVarField(parent: Parent, position: Int, varField: Json)
  private case class IndexedFixedField(parent: Parent, code: String, fixedField: Json)

  def split(t: SierraTransformable): Either[Seq[(Parent, ParsingFailure)], (Seq[IndexRequest], Seq[DeleteByQueryRequest])] = {
    for {
      apiData <- getSierraApiData(t)

      mainRecords = List(
        apiData.map { case (parent, json) =>
          IndexRequest(
            index = Index(s"${indexPrefix}_${parent.recordType}"),
            id = Some(parent.id.withoutCheckDigit),
            source = Some(json.remainder.noSpaces)
          )
        }
      ).flatten

      varFields = apiData.flatMap { case (parent, json) =>
        json.varFields.zipWithIndex
          .map {
            case (varField, position) =>
              IndexRequest(
                index = varFieldIndex,
                id = Some(s"${parent.id}-$position"),
                source = Some(
                  IndexedVarField(parent, position, varField)
                    .asJson
                    .noSpaces
                )
              )
          }
      }

      varFieldDeletions = apiData.map { case (parent, json) =>
        DeleteByQueryRequest(
          indexes = Indexes(varFieldIndex.name),
          query =
            must(
              termQuery("parent.id.keyword", parent.id),
              termQuery("parent.recordType.keyword", parent.recordType.toString),
              rangeQuery("position").gte(json.varFields.length)
            )
        )
      }

      fixedFields = apiData.flatMap { case (parent, json) =>
        json.fixedFields
          .map {
            case (code, fixedField) =>
              IndexRequest(
                index = fixedFieldsIndex,
                id = Some(s"${parent.id}-$code"),
                source = Some(
                  IndexedFixedField(parent, code, fixedField)
                    .asJson
                    .noSpaces
                )
              )
          }
      }

      fixedFieldDeletions = apiData.map { case (parent, json) =>
        DeleteByQueryRequest(
          indexes = Indexes(varFieldIndex.name),
          query =
            must(termQuery("parent.id", parent.id))
              .not(
                termsQuery("code", json.fixedFields.keys)
              )
        )
      }
    } yield (
      mainRecords ++ varFields ++ fixedFields,
      varFieldDeletions ++ fixedFieldDeletions
    )
  }

  private def getSierraApiData(t: SierraTransformable): Either[Seq[(Parent, ParsingFailure)], Seq[(Parent, Json)]] = {
    val bibData = t.maybeBibRecord match {
      case Some(bibRecord) =>
        Seq(
          Parent(SierraRecordTypes.bibs, bibRecord.id) -> parse(bibRecord.data)
        )
      case None => Seq()
    }

    val itemData: Seq[(Parent, Either[ParsingFailure, Json])] = t.itemRecords
      .values
      .map { itemRecord =>
        Parent(SierraRecordTypes.items, itemRecord.id) -> parse(itemRecord.data)
      }
      .toSeq

    val holdingsData: Seq[(Parent, Either[ParsingFailure, Json])] = t.holdingsRecords
      .values
      .map { holdingsRecord =>
        Parent(SierraRecordTypes.holdings, holdingsRecord.id) -> parse(holdingsRecord.data)
      }
      .toSeq

    val data = bibData ++ itemData ++ holdingsData

    val successes = data.collect { case (parent, Right(json)) => (parent, json) }
    val failures = data.collect { case (parent, Left(err)) => (parent, err) }

    Either.cond(failures.isEmpty, successes, failures)
  }
}
