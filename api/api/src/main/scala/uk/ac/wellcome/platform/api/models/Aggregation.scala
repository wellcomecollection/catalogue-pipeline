package uk.ac.wellcome.platform.api.models

import scala.util.{Failure, Success, Try}
import io.circe.Decoder
import java.time.{Instant, LocalDateTime, ZoneOffset}

import com.sksamuel.elastic4s.requests.searches.SearchResponse
import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.json.JsonUtil._

case class Aggregations(
  workType: Option[Aggregation[WorkType]] = None,
  genres: Option[Aggregation[Genre[Minted[AbstractConcept]]]] = None,
  productionDates: Option[Aggregation[Period]] = None,
  language: Option[Aggregation[Language]] = None,
  subjects: Option[Aggregation[Subject[Minted[AbstractRootConcept]]]] = None,
  license: Option[Aggregation[License]] = None,
)

object Aggregations extends Logging {

  def apply(searchResponse: SearchResponse): Option[Aggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        Aggregations(
          workType = e4sAggregations.decodeAgg[WorkType]("workType"),
          genres =
            e4sAggregations.decodeAgg[Genre[Minted[AbstractConcept]]]("genres"),
          productionDates = e4sAggregations.decodeAgg[Period]("productionDates"),
          language = e4sAggregations.decodeAgg[Language]("language"),
          subjects = e4sAggregations
            .decodeAgg[Subject[Minted[AbstractRootConcept]]]("subjects"),
          license = e4sAggregations.decodeAgg[License]("license")
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

  implicit val decodeWorkType: Decoder[WorkType] =
    Decoder.decodeString.emap { str =>
      WorkType.fromCode(str) match {
        case Some(workType) => Right(workType)
        case None           => Left(s"couldn't find workType for code '$str'")
      }
    }

  implicit val decodeGenreFromLabel: Decoder[Genre[Minted[AbstractConcept]]] =
    Decoder.decodeString.map { str =>
      Genre(label = str)
    }

  implicit val decodeSubjectFromLabel
    : Decoder[Subject[Minted[AbstractRootConcept]]] =
    Decoder.decodeString.map { str =>
      Subject(label = str)
    }

  implicit class EnhancedEsAggregations(
    aggregations: com.sksamuel.elastic4s.requests.searches.Aggregations) {

    def decodeAgg[T: Decoder](name: String): Option[Aggregation[T]] = {
      aggregations
        .getAgg(name)
        .flatMap(
          _.safeTo[Aggregation[T]]((json: String) =>
            AggregationMapping.aggregationParser(name, json)).recoverWith {
            case err =>
              warn("Failed to parse aggregation from ES", err)
              Failure(err)
          }.toOption
        )
    }
  }
}

object AggregationMapping extends Logging {
  import io.circe.parser._
  import io.circe.optics.JsonPath._

  private val buckets = root.buckets.arr
  private val bucketFilteredCount = root.filtered.doc_count.int
  private val bucketRootCount = root.doc_count.int
  private val bucketKey = root.key.json
  private val bucketSampleDoc =
    root.sample_doc.hits.hits.index(0)._source.data.json

  def aggregationParser[T: Decoder](name: String,
                                    json: String): Try[Aggregation[T]] = {
    val parsedJson = parse(json) match {
      case Right(json) => json
      case Left(err) =>
        return Failure(err)
    }
    val bucketMaybes = for {
      bucket <- buckets.getOption(parsedJson).getOrElse(List())
      bucketDoc <- bucketSampleDoc
        .getOption(bucket)
        .flatMap(_.hcursor.downField(name).focus)
        .orElse(bucketKey.getOption(bucket))
        .map(_.as[T])
      bucketCount <- bucketFilteredCount
        .getOption(bucket)
        .orElse(bucketRootCount.getOption(bucket))
    } yield {
      (bucketCount, bucketDoc) match {
        case (0, _)           => None
        case (n, Right(data)) => Some(AggregationBucket(data, n))
        case (_, Left(err)) =>
          warn("Failed to parse aggregation from ES", err)
          return Failure(err)
      }
    }
    Success(Aggregation(bucketMaybes.toList.flatten))
  }

}

case class Aggregation[T](buckets: List[AggregationBucket[T]])
case class AggregationBucket[T](data: T, count: Int)
