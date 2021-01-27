package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.SearchResponse
import io.circe.Decoder
import uk.ac.wellcome.models.work.internal._

import scala.util.Try

case class ImageAggregations(
  license: Option[Aggregation[License]] = None,
)

object ImageAggregations extends ElasticAggregations {
  def apply(searchResponse: SearchResponse): Option[ImageAggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        ImageAggregations(
          license = e4sAggregations.decodeAgg[License]("license")
        ))
    } else {
      None
    }
  }

  implicit val decodeLicense: Decoder[License] =
    Decoder.decodeString.emap { str =>
      Try(License.createLicense(str)).toEither.left
        .map(err => err.getMessage)
    }
}
