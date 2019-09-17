package uk.ac.wellcome.platform.api.models

import io.circe.{Decoder, Json}
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.{WorkType, Period}
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Success}

trait OntologyType {
  val ontologyType: String
}
object OntologyType {}

// TODO: Return a `ParseError`s where applicable
case class Genre(label: String)

case class Aggregations(workType: Option[Aggregation[WorkType]] = None,
                        genre: Option[Aggregation[Genre]] = None,
                        date: Option[Aggregation[Period]] = None)

object Aggregations extends Logging {
  val validAggregationRequests = List("workType", "genre")

  def isEmpty(a: Aggregations): Boolean =
    a.workType.isEmpty && a.genre.isEmpty

  def getFromJson[T](json: Json)(
    implicit decoderT: Decoder[T],
    decoderEsAgg: Decoder[EsAggregation]): Option[Aggregation[T]] =
    decoderEsAgg.decodeJson(json).right.toOption.map { esAgg =>
      Aggregation(
        esAgg.buckets.flatMap { esAggBucket =>
          decoderT.decodeJson(esAggBucket.key).right.toOption.map {
            data => AggregationBucket(data, esAggBucket.doc_count)
          }
        }
      )
    }

  def apply(jsonString: String): Option[Aggregations] =
    fromJson[Map[String, Json]](jsonString) match {
      case Failure(error) =>
        warn(s"Error decoding elasticsearch json response: ${error}")
        None
      case Success(jsonMap) =>
        jsonMap.size match {
          case 0 => None
          case _ =>
            val workType =
              jsonMap
                .get("workType")
                .flatMap(json => getFromJson[WorkType](json))
            val genre =
              jsonMap.get("genre").flatMap(json => getFromJson[Genre](json))
            // val dates =
            //   jsonMap.get("date").flatMap(json => getFromJson[Genre](json))

            Some(Aggregations(workType = workType, genre = genre))
        }
    }
}

case class Aggregation[T](buckets: List[AggregationBucket[T]])
case class AggregationBucket[T](data: T, count: Int)

/**
  * We use these to convert the JSON into Elasticsearch case classes (not supplied via elastic4s)
  * and then convert them into our representations of aggregations.
  * This is to get around things like having property names like `doc_count` and `key`
  * The general format here is:
  *
  * {
  *   "buckets": {
  *     "key": {
  *       "this": "is",
  *       "structured": "content"
  *     },
  *     "doc_count" 1009
  *   }
  * }
  *
  * And we convert them to:
  * {
  *   "buckets": {
  *     "data": {
  *       "this": "is",
  *       "structured": "content"
  *     },
  *     "count" 1009
  *   }
  * }
  */
case class EsAggregation(buckets: List[EsAggregationBucket])
case class EsAggregationBucket(key: Json, doc_count: Int)
