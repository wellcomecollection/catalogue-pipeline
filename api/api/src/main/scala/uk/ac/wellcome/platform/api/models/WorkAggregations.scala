package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.SearchResponse
import io.circe.Decoder
import uk.ac.wellcome.models.marc.MarcLanguageCodeList
import uk.ac.wellcome.models.work.internal.IdState.Minted
import uk.ac.wellcome.models.work.internal._

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.util.Try

case class WorkAggregations(
  format: Option[Aggregation[Format]] = None,
  genres: Option[Aggregation[Genre[Minted]]] = None,
  productionDates: Option[Aggregation[Period[Minted]]] = None,
  languages: Option[Aggregation[Language]] = None,
  subjects: Option[Aggregation[Subject[Minted]]] = None,
  contributors: Option[Aggregation[Contributor[Minted]]] = None,
  license: Option[Aggregation[License]] = None,
  availabilities: Option[Aggregation[Availability]] = None,
)

object WorkAggregations extends ElasticAggregations {

  def apply(searchResponse: SearchResponse): Option[WorkAggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        WorkAggregations(
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
          availabilities =
            e4sAggregations.decodeAgg[Availability]("availabilities")
        ))
    } else {
      None
    }
  }

  // Elasticsearch encodes the date key as milliseconds since the epoch
  implicit val decodePeriodFromEpochMilli: Decoder[Period[Minted]] =
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

  implicit val decodeFormatFromId: Decoder[Format] =
    Decoder.decodeString.emap(Format.withNameEither(_).getMessage)

  implicit val decodeAvailabilityFromId: Decoder[Availability] =
    Decoder.decodeString.emap(Availability.withNameEither(_).getMessage)

  // Both the Calm and Sierra transformers use the MARC language code list
  // to populate the "languages" field, so we can use the ID (code) to
  // unambiguously identify a language.
  implicit val decodeLanguageFromCode: Decoder[Language] =
    Decoder.decodeString.emap { code =>
      MarcLanguageCodeList.lookupByCode(code) match {
        case Some(lang) => Right(lang)
        case None       => Left(s"couldn't find language for code $code")
      }
    }

  implicit val decodeSubjectFromLabel: Decoder[Subject[Minted]] =
    Decoder.decodeString.map { str =>
      Subject(label = str, concepts = Nil)
    }

  implicit val decodeContributorFromLabel: Decoder[Contributor[Minted]] =
    decodeAgentFromLabel.map(agent => Contributor(agent = agent, roles = Nil))

}
