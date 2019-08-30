package uk.ac.wellcome.platform.api.models

import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.json.JsonUtil._

trait AggregationResult
case class AggregationResults(workType: Option[AggregationBuckets],
                              dates: Option[AggregationBuckets])
object AggregationResults {
  def apply(vals: Map[String, Any], json: String): Option[AggregationResults] =
    fromJson[AggregationResults](json) toOption
}
case class AggregationBuckets(buckets: List[AggregationBucket])

case class AggregationBucket(key: String, count: Int)

// We use these semiauto derivations so we don't have to use the elastic naming of `doc_count`
object AggregationBucket {
  implicit val decodeAggregationBucket: Decoder[AggregationBucket] =
    Decoder.forProduct2("key", "doc_count")(AggregationBucket.apply)

  implicit val encodeAggregationBucket: Encoder[AggregationBucket] =
    Encoder.forProduct2("key", "doc_count")(a => (a.key, a.count))
}
