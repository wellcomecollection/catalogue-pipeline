package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.aggs.responses.{
  Aggregations => Elastic4sAggregations
}
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.models.work.internal.IdState.Minted
import uk.ac.wellcome.models.work.internal.{
  AbstractAgent,
  Agent,
  Genre,
  License,
  Meeting,
  Organisation,
  Person
}

import scala.util.Failure

trait ElasticAggregations extends Logging {

  implicit val decodeLicenseFromId: Decoder[License] =
    Decoder.decodeString.emap(License.withNameEither(_).getMessage)

  implicit val decodeAgentFromLabel: Decoder[AbstractAgent[Minted]] =
    Decoder.decodeString.emap { str =>
      val splitIdx = str.indexOf(':')
      val ontologyType = str.slice(0, splitIdx)
      val label = str.slice(splitIdx + 1, Int.MaxValue)
      ontologyType match {
        case "Agent"        => Right(Agent(label = label))
        case "Person"       => Right(Person(label = label))
        case "Organisation" => Right(Organisation(label = label))
        case "Meeting"      => Right(Meeting(label = label))
        case ontologyType   => Left(s"Illegal agent type: $ontologyType")
      }
    }

  implicit val decodeGenreFromLabel: Decoder[Genre[Minted]] =
    Decoder.decodeString.map { str =>
      Genre(label = str)
    }

  implicit class ThrowableEitherOps[T](either: Either[Throwable, T]) {
    def getMessage: Either[String, T] =
      either.left.map(_.getMessage)
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
