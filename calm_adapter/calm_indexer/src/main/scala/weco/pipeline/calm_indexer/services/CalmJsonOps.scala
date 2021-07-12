package weco.pipeline.calm_indexer.services

import io.circe.{Json, JsonObject}

object CalmJsonOps {
  implicit class JsonOps(j: Json) {
    def tidy: Json =
      j.mapObject { jsonObj =>
        val fields =
          jsonObj
            .toMap
            .map { case (key, jsonArray) => key -> jsonArray.extractSingleValue }
            .filterNot { case (_, json) => json == Json.fromString("") }
            .toSeq

        JsonObject(fields: _*)
      }

    protected def extractSingleValue: Json =
      j.asArray match {
        case Some(Seq(value)) => value
        case _                => j
      }

    protected def isOnlyEmptyString: Boolean =
      j == Json.fromValues(Seq(Json.fromString("")))
  }
}
