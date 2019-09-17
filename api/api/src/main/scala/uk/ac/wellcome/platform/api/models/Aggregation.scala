package uk.ac.wellcome.platform.api.models

import scala.util.Try
import io.circe.{Decoder, Json}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.{WorkType, Period}
import uk.ac.wellcome.json.JsonUtil._

trait OntologyType {
  val ontologyType: String
}
object OntologyType {}

// TODO: Return a `ParseError`s where applicable
case class Genre(label: String)

case class Aggregations(workType: Option[Aggregation[WorkType]] = None,
                        genre: Option[Aggregation[Genre]] = None,
                        year: Option[Aggregation[Period]] = None)

object Aggregations extends Logging {

  def apply(jsonString: String): Option[Aggregations] =
    fromJson[Map[String, Json]](jsonString)
      .collect {
        case jsonMap if jsonMap.nonEmpty =>
          Some(
            Aggregations(
              workType = getAggregation[WorkType]("workType", jsonMap),
              genre    = getAggregation[Genre]("genre", jsonMap),
              year     = getAggregation[Period]("year", jsonMap),
            )
          )
      }
      .getOrElse { None }

  def getAggregation[T](key: String, jsonMap: Map[String, Json])(
    implicit
    decoder: Decoder[EsAggregation[T]]): Option[Aggregation[T]] =
    jsonMap.get(key).flatMap(Aggregation.fromJson[T])

  // Elasticsearch encodes the date key as milliseconds since the epoch
  implicit val decodePeriod: Decoder[Period] =
    Decoder.decodeLong.emap { epochMilli =>
      Try { Instant.ofEpochMilli(epochMilli) }
        .map { instant =>  LocalDateTime.ofInstant(instant, ZoneOffset.UTC) }
        .map { date => Right(Period(date.getYear.toString)) }
        .getOrElse { Left("Error decoding") }
    }
}

case class Aggregation[T](buckets: List[AggregationBucket[T]])

object Aggregation extends Logging {

  def fromJson[T](json: Json)(
    implicit
    decoder: Decoder[EsAggregation[T]]): Option[Aggregation[T]] = {
    decoder
      .decodeJson(json)
      .left.map { error => warn(s"Error decoding ES aggregation: $error") }
      .toOption
      .map { esAgg =>
        Aggregation(
          esAgg.buckets.map { esAggBucket =>
            AggregationBucket(esAggBucket.key, esAggBucket.doc_count)
          }
        )
      }
    }
}

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
case class EsAggregation[T](buckets: List[EsAggregationBucket[T]])
case class EsAggregationBucket[T](key: T, doc_count: Int)
