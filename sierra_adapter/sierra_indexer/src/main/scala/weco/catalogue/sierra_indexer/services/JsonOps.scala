package weco.catalogue.sierra_indexer.services

import io.circe.Json

object JsonOps {
  implicit class JsonOps(j: Json) {
    def varFields: List[Json] =
      j.hcursor.downField("varFields").as[List[Json]].getOrElse(List())

    def fixedFields: Map[String, Json] =
      j.hcursor.downField("fixedFields").as[Map[String, Json]].getOrElse(Map())

    def remainder: Json =
      j.mapObject {
        _.remove("varFields").remove("fixedFields")
      }
  }
}
