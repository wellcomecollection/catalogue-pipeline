package uk.ac.wellcome.platform.api.models

import scala.util.{Failure, Try}
import io.circe.Decoder
import java.time.{Instant, LocalDateTime, ZoneOffset}

import com.sksamuel.elastic4s.AggReader
import com.sksamuel.elastic4s.requests.searches.{SearchResponse, Transformable}
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.json.JsonUtil._

case class Aggregations(
  workType: Option[Aggregation[WorkType]] = None,
  genres: Option[Aggregation[Genre[Minted[AbstractConcept]]]] = None,
  productionDates: Option[Aggregation[Period]] = None,
  language: Option[Aggregation[Language]] = None,
  subjects: Option[Aggregation[Subject[Minted[AbstractRootConcept]]]] =
    None,
  license: Option[Aggregation[License]] = None,
)

object Aggregations extends Logging {

  def apply(searchResponse: SearchResponse): Option[Aggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        Aggregations(
          workType = e4sAggregations
            .getAgg("workType")
            .flatMap(_.toAgg[WorkType]),
          genres = e4sAggregations
            .getAgg("genres")
            .flatMap(_.toAgg[Genre[Minted[AbstractConcept]]]),
          productionDates = e4sAggregations
            .getAgg("productionDates")
            .flatMap(_.toAgg[Period]),
          language = e4sAggregations
            .getAgg("language")
            .flatMap(_.toAgg[Language]),
          subjects = e4sAggregations
            .getAgg("subjects")
            .flatMap(_.toAgg[Subject[Minted[AbstractRootConcept]]]),
          license = e4sAggregations
            .getAgg("license")
            .flatMap(_.toAgg[License])
        ))
    } else {
      None
    }
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

  implicit class EnhancedTransformable(transformable: Transformable) {
    def toAgg[T: Decoder]: Option[Aggregation[T]] = {
      transformable
        .safeTo[EsAggregation[T]]
        .recoverWith {
          case err =>
            warn("Failed to parse aggregation from ES", err)
            Failure(err)
        }
        .toOption
        .map(
          agg =>
            Aggregation(
              agg.buckets
                .map(getFilteredBucket)
                .filter(_.count != 0)))
    }

    /** If the `filtered` sub-aggregation is present (see [[uk.ac.wellcome.platform.api.services.FiltersAndAggregationsBuilder]])
      * then use the count from it */
    private def getFilteredBucket[T](
      esAggBucket: EsAggregationBucket[T]): AggregationBucket[T] =
      AggregationBucket(
        esAggBucket.key,
        esAggBucket.filtered
          .map(_.doc_count)
          .getOrElse(esAggBucket.doc_count))

    implicit def fromJsonAggReader[T: Decoder]: AggReader[T] =
      (json: String) => fromJson[T](json)
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
case class EsAggregation[T](buckets: List[EsAggregationBucket[T]])

/** The `filtered` key is introduced by the nested aggregations requested by
  * [[uk.ac.wellcome.platform.api.services.FiltersAndAggregationsBuilder]] */
case class EsAggregationBucket[T](key: T,
                                  doc_count: Int,
                                  filtered: Option[EsAggregationSubAgg])
case class EsAggregationSubAgg(doc_count: Int)
