package weco.pipeline.sierra_indexer.services

import io.circe.Json
import weco.sierra.models.identifiers.TypedSierraRecordNumber

object SierraJsonOps {
  implicit class JsonOps(j: Json) {
    def varFields: List[Json] =
      j.hcursor.downField("varFields").as[List[Json]].getOrElse(List())

    def fixedFields: Map[String, Json] =
      j.hcursor.downField("fixedFields").as[Map[String, Json]].getOrElse(Map())

    def remainder: Json =
      j.mapObject {
        _.remove("varFields").remove("fixedFields")
      }

    def withId(id: TypedSierraRecordNumber): Json =
      j.mapObject {
        _.add("idWithCheckDigit", Json.fromString(id.withCheckDigit))
      }
  }
}
