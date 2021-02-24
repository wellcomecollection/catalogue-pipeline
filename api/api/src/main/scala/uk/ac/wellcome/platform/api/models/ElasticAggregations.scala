package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.aggs.responses.{
  Aggregations => Elastic4sAggregations
}
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.models.work.internal.License

import scala.util.Failure

trait ElasticAggregations extends Logging {

  implicit val decodeLicenseFromId: Decoder[License] =
    Decoder.decodeString.emap(License.withNameEither(_).getMessage)

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
