package uk.ac.wellcome.platform.api.models

import scala.util.Try
import io.circe.Decoder
import java.time.{Instant, LocalDateTime, ZoneOffset}

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.json.JsonUtil._

case class Aggregations(
  workType: Option[Aggregation[WorkType]] = None,
  genres: Option[Aggregation[Genre[Displayable[AbstractConcept]]]] = None,
  productionDates: Option[Aggregation[Period]] = None,
  language: Option[Aggregation[Language]] = None,
  subjects: Option[Aggregation[Subject[Displayable[AbstractRootConcept]]]] =
    None,
  license: Option[Aggregation[License]] = None,
)

object Aggregations extends Logging {

  def apply(jsonString: String): Option[Aggregations] =
    fromJson[EsAggregations](jsonString)
      .collect {
        case esAggs if esAggs.nonEmpty =>
          Some(
            Aggregations(
              workType = getAggregation[WorkType](esAggs.workType),
              genres = getAggregation[Genre[Displayable[AbstractConcept]]](
                esAggs.genres),
              productionDates = getAggregation[Period](esAggs.productionDates)
                .map(DateAggregationMerger(_)),
              language = getAggregation[Language](esAggs.language),
              subjects =
                getAggregation[Subject[Displayable[AbstractRootConcept]]](
                  esAggs.subjects),
              license = getAggregation[License](esAggs.license),
            )
          )
      }
      .getOrElse { None }

  def getAggregation[T](
    maybeEsAgg: Option[EsAggregation[T]]): Option[Aggregation[T]] =
    maybeEsAgg.map { esAgg =>
      Aggregation(
        esAgg.buckets.map { esAggBucket =>
          AggregationBucket(esAggBucket.key, esAggBucket.doc_count)
        }
      )
    }

  // Elasticsearch encodes the date key as milliseconds since the epoch
  implicit val decodePeriod: Decoder[Period] =
    Decoder.decodeLong.emap { epochMilli =>
      Try { Instant.ofEpochMilli(epochMilli) }
        .map { instant =>
          LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
        }
        .map { date =>
          Right(Period(date.getYear.toString))
        }
        .getOrElse { Left("Error decoding") }
    }

  implicit val decodeLicense: Decoder[License] =
    Decoder.decodeString.emap { str =>
      Try(License.createLicense(str)).toEither.left
        .map(err => err.getMessage)
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
case class EsAggregations(
  workType: Option[EsAggregation[WorkType]] = None,
  genres: Option[EsAggregation[Genre[Displayable[AbstractConcept]]]] = None,
  productionDates: Option[EsAggregation[Period]] = None,
  language: Option[EsAggregation[Language]] = None,
  subjects: Option[EsAggregation[Subject[Displayable[AbstractRootConcept]]]] =
    None,
  license: Option[EsAggregation[License]] = None
) {
  def nonEmpty: Boolean =
    List(workType, genres, productionDates, language, subjects, license).flatten.nonEmpty
}

case class EsAggregation[T](buckets: List[EsAggregationBucket[T]])

case class EsAggregationBucket[T](key: T, doc_count: Int)
