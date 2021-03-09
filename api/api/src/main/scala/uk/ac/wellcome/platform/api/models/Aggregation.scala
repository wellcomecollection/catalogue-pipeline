package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.JsonKey
import io.circe.{Decoder, Json}

import scala.util.Try

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
