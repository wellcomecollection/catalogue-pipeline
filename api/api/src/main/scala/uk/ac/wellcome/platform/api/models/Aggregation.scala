package uk.ac.wellcome.platform.api.models

import scala.util.{Failure, Try}
import java.time.{Instant, LocalDateTime, ZoneOffset}
import com.sksamuel.elastic4s.requests.searches.aggs.responses.{
  Aggregations => Elastic4sAggregations
}
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import grizzled.slf4j.Logging
import io.circe.generic.extras.JsonKey
import io.circe.{Decoder, Json}

import uk.ac.wellcome.display.models.LocationTypeQuery
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.marc.MarcLanguageCodeList
import uk.ac.wellcome.models.work.internal._
import IdState.Minted

case class Aggregations(
  format: Option[Aggregation[Format]] = None,
  genres: Option[Aggregation[Genre[Minted]]] = None,
  productionDates: Option[Aggregation[Period[Minted]]] = None,
  languages: Option[Aggregation[Language]] = None,
  subjects: Option[Aggregation[Subject[Minted]]] = None,
  contributors: Option[Aggregation[Contributor[Minted]]] = None,
  license: Option[Aggregation[License]] = None,
  locationType: Option[Aggregation[LocationTypeQuery]] = None,
)

object Aggregations extends Logging {

  def apply(searchResponse: SearchResponse): Option[Aggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        Aggregations(
          format = e4sAggregations.decodeAgg[Format]("format"),
          genres = e4sAggregations.decodeAgg[Genre[Minted]]("genres"),
          productionDates = e4sAggregations
            .decodeAgg[Period[Minted]]("productionDates"),
          languages = e4sAggregations.decodeAgg[Language]("languages"),
          subjects = e4sAggregations
            .decodeAgg[Subject[Minted]]("subjects"),
          contributors = e4sAggregations
            .decodeAgg[Contributor[Minted]]("contributors"),
          license = e4sAggregations.decodeAgg[License]("license"),
          locationType =
            e4sAggregations.decodeAgg[LocationTypeQuery]("locationType")
        ))
    } else {
      None
    }
  }

  // Elasticsearch encodes the date key as milliseconds since the epoch
  implicit val decodePeriod: Decoder[Period[Minted]] =
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

  implicit val decodeFormat: Decoder[Format] =
    Decoder.decodeString.emap { str =>
      Format.fromCode(str) match {
        case Some(format) => Right(format)
        case None         => Left(s"couldn't find format for code '$str'")
      }
    }

  // Both the Calm and Sierra transformers use the MARC language code list
  // to populate the "languages" field, so we can use the ID (code) to
  // unambiguously identify a language.
  implicit val decodeLanguage: Decoder[Language] =
    Decoder.decodeString.emap { code =>
      MarcLanguageCodeList.lookupByCode(code) match {
        case Some(lang) => Right(lang)
        case None       => Left(s"couldn't find language for code $code")
      }
    }

  implicit val decodeGenreFromLabel: Decoder[Genre[Minted]] =
    Decoder.decodeString.map { str =>
      Genre(label = str)
    }

  implicit val decodeSubjectFromLabel: Decoder[Subject[Minted]] =
    Decoder.decodeString.map { str =>
      Subject(label = str, concepts = Nil)
    }

  implicit val decodeContributorFromLabel: Decoder[Contributor[Minted]] =
    Decoder.decodeString.map { str =>
      Contributor(agent = Agent(label = str), roles = Nil)
    }

  implicit val decodeLocationTypeFromLabel: Decoder[LocationTypeQuery] =
    Decoder.decodeString.map {
      case "DigitalLocationDeprecated"  => LocationTypeQuery.DigitalLocation
      case "PhysicalLocationDeprecated" => LocationTypeQuery.PhysicalLocation
    }

  implicit class EnhancedEsAggregations(aggregations: Elastic4sAggregations) {

    def decodeAgg[T: Decoder](name: String): Option[Aggregation[T]] = {
      aggregations
        .getAgg(name)
        .flatMap(
          _.safeTo[Aggregation[T]](
            (json: String) => AggregationMapping.aggregationParser[T](json)
          ).recoverWith {
            case err =>
              warn("Failed to parse aggregation from ES", err)
              Failure(err)
          }.toOption
        )
    }
  }
}

// This object maps an aggregation result from Elasticsearch into our
// Aggregation data type.  The results from ES are of the form:
//
//    {
//      "buckets": [
//        {
//          "key": "chi",
//          "doc_count": 4,
//          "filtered": {
//            "doc_count": 3
//          }
//        },
//        ...
//      ],
//      ...
//    }
//
// If the buckets have a subaggregation named "filtered", then we use the
// count from there; otherwise we use the count from the root of the bucket.
object AggregationMapping {

  import uk.ac.wellcome.json.JsonUtil._

  private case class Result(buckets: Seq[Bucket])

  private case class Bucket(
    key: Json,
    @JsonKey("doc_count") count: Int,
    filtered: Option[FilteredResult]
  ) {
    def docCount: Int =
      filtered match {
        case Some(f) => f.count
        case None    => count
      }
  }

  private case class FilteredResult(@JsonKey("doc_count") count: Int)

  def aggregationParser[T: Decoder](jsonString: String): Try[Aggregation[T]] =
    fromJson[Result](jsonString)
      .map { result =>
        result.buckets
          .map { b =>
            (b.key.as[T], b.docCount)
          }
      }
      .map { tally =>
        tally.collect {
          case (Right(t), count) => AggregationBucket(t, count = count)
        }
      }
      .map { buckets =>
        Aggregation(buckets.toList)
      }
}

case class Aggregation[+T](buckets: List[AggregationBucket[T]])
case class AggregationBucket[+T](data: T, count: Int)
