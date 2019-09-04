package uk.ac.wellcome.platform.api.models

import io.circe.{Decoder, Json}
import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Success}

// TODO: Return a `ParseError`s where applicable
case class Genre(label: String)
case class AggregationSet(workType: Option[Aggregation[WorkType]],
                          genre: Option[Aggregation[Genre]])
object AggregationSet {
  val validAggregationRequests = List("workType", "genre")

  def isEmpty(a: AggregationSet): Boolean =
    a.workType.isEmpty && a.genre.isEmpty

  // Convert the JSON into Elasticsearch case classes (not supplied via elastic4s)
  // and then convert them to our representations of aggregations.
  // This is to get around things like having property names like `doc_count` and `key`
  def getFromJson[ParsedType](json: Json)(
    implicit decoder: Decoder[ParsedType]): Option[Aggregation[ParsedType]] = {
    fromJson[EsAggregation](json.toString).toOption.map(esAggregation => {
      val buckets = esAggregation.buckets.flatMap(esAggregationBucket =>
        fromJson[ParsedType](esAggregationBucket.key.toString()) map (data =>
          AggregationBucket(data, esAggregationBucket.doc_count)) toOption)

      Aggregation(buckets = buckets)
    })
  }

  def apply(jsonString: String): AggregationSet = {
    val json = fromJson[Map[String, Json]](jsonString)

    json match {
      case Failure(_) => AggregationSet(workType = None, genre = None)
      case Success(jsonMap) => {
        val workType =
          jsonMap.get("workType").flatMap(json => getFromJson[WorkType](json))
        val genre =
          jsonMap.get("genre").flatMap(json => getFromJson[Genre](json))

        AggregationSet(workType = workType, genre = genre)
      }
    }
  }
}

case class Aggregation[T](buckets: List[AggregationBucket[T]])
case class AggregationBucket[T](data: T, count: Int)

case class EsAggregation(buckets: List[EsAggregationBucket])
case class EsAggregationBucket(key: Json, doc_count: Int)
