package weco.catalogue.sierra_indexer.models

import com.sksamuel.elastic4s.ElasticApi.{must, rangeQuery, termQuery, termsQuery}
import com.sksamuel.elastic4s.requests.delete.DeleteByQueryRequest
import com.sksamuel.elastic4s.{Index, Indexes}
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import io.circe.Json
import io.circe.syntax._
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.sierra_adapter.models.Implicits._
import weco.catalogue.sierra_indexer.services.SierraJsonOps._

object IndexerRequest {
  def mainRecords(indexPrefix: String, apiData: Seq[(Parent, Json)]): Seq[IndexRequest] =
    List(
      apiData.map {
        case (parent, json) =>
          IndexRequest(
            index = Index(s"${indexPrefix}_${parent.recordType}"),
            id = Some(parent.id.withoutCheckDigit),
            source = Some(json.remainder.noSpaces)
          )
      }
    ).flatten

  private def varFieldIndex(indexPrefix: String) = Index(s"${indexPrefix}_varfields")
  private def fixedFieldIndex(indexPrefix: String) = Index(s"${indexPrefix}_fixedfields")

  private case class IndexedVarField(
    parent: Parent,
    position: Int,
    varField: Json
  )

  def varFields(indexPrefix: String, apiData: Seq[(Parent, Json)]): Seq[IndexRequest] =
    apiData.flatMap {
      case (parent, json) =>
        json.varFields.zipWithIndex
          .map {
            case (varField, position) =>
              IndexRequest(
                index = varFieldIndex(indexPrefix),
                id = Some(s"${parent.id}-$position"),
                source = Some(
                  IndexedVarField(parent, position, varField).asJson.noSpaces
                )
              )
          }
    }

  def varFieldDeletions(indexPrefix: String, apiData: Seq[(Parent, Json)]): Seq[DeleteByQueryRequest] =
    apiData.map {
      case (parent, json) =>
        DeleteByQueryRequest(
          indexes = Indexes(varFieldIndex(indexPrefix).name),
          query = must(
            termQuery("parent.id.keyword", parent.id),
            termQuery(
              "parent.recordType.keyword",
              parent.recordType.toString),
            rangeQuery("position").gte(json.varFields.length)
          )
        )
    }

  private case class IndexedFixedField(
    parent: Parent,
    code: String,
    fixedField: Json
  )

  def fixedFields(indexPrefix: String, apiData: Seq[(Parent, Json)]): Seq[IndexRequest] =
    apiData.flatMap {
      case (parent, json) =>
        json.fixedFields
          .map {
            case (code, fixedField) =>
              IndexRequest(
                index = fixedFieldIndex(indexPrefix),
                id = Some(s"${parent.id}-$code"),
                source = Some(
                  IndexedFixedField(parent, code, fixedField).asJson.noSpaces
                )
              )
          }
    }

  def fixedFieldDeletions(indexPrefix: String, apiData: Seq[(Parent, Json)]): Seq[DeleteByQueryRequest] =
    apiData.map {
      case (parent, json) =>
        DeleteByQueryRequest(
          indexes = Indexes(fixedFieldIndex(indexPrefix).name),
          query = must(
            termQuery("parent.id", parent.id),
            termQuery(
              "parent.recordType.keyword",
              parent.recordType.toString),
          )
            .not(
              termsQuery("code", json.fixedFields.keys)
            )
        )
    }
}
