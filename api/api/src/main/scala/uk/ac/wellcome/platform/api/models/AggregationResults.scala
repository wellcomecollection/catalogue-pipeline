package uk.ac.wellcome.platform.api.models

import io.circe.{Decoder}
import io.circe._, io.circe.generic.semiauto._
import io.circe.Json
import io.circe.parser._
import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.json.JsonUtil._

trait AggregationResult
case class AggregationResults(
  workType: Option[AggregationBuckets[WorkTypeAggregationBucket]])
object AggregationResults {
  def jsonToAgg[TypedAggregationBucket](json: Json, key: String)(
    implicit decoder: Decoder[TypedAggregationBucket])
    : List[TypedAggregationBucket] =
    json.hcursor
      .downField(key)
      .downField("buckets")
      .focus
      .flatMap(_.asArray)
      .map { jsonArr =>
        jsonArr flatMap { bucket =>
          fromJson[TypedAggregationBucket](bucket.toString) toOption
        }
      }
      .map(_.toList)
      .getOrElse(Nil)

  def apply(vals: Map[String, Any], json: String): Option[AggregationResults] =
    parse(json) match {
      case Left(r) => None
      case Right(json) => {
        implicit val decoder: Decoder[WorkTypeAggregationBucket] =
          deriveDecoder[WorkTypeAggregationBucket]

        val workTypeBuckets =
          jsonToAgg[WorkTypeAggregationBucket](json, "workType")

        Some(
          AggregationResults(
            workType = Some(AggregationBuckets(buckets = workTypeBuckets))))
      }
    }
}
case class AggregationBuckets[T](buckets: List[T])
case class AggregationBucket[T](data: T, doc_count: Int)
case class WorkTypeAggregationBucket(key: WorkType, doc_count: Int)

// We use these semiauto derivations so we don't have to use the elastic naming of `doc_count`
//object AggregationBucket {
////  implicit val decodeWorkAggregationBucket
//
//  implicit val decodeWorkAggregationBucket
//    : Decoder[AggregationBucket[WorkType]] =
//    Decoder.forProduct2("key", "doc_count")(AggregationBucket.apply)
//
//  implicit val encodeWorkAggregationBucket
//    : Encoder[AggregationBucket[WorkType]] =
//    Encoder.forProduct2("key", "doc_count")(a =>
//      (toJson[WorkType](a.data), a.count))
//}
