package uk.ac.wellcome.platform.api.models

import io.circe.{Decoder, Json}
import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.json.JsonUtil._

import scala.util.{Failure, Success}

trait OntologyType {
  val ontologyType: String
}
object OntologyType {}

// TODO: Return a `ParseError`s where applicable
case class Genre(label: String)
case class Aggregations(workType: Option[Aggregation[WorkType]],
                        genre: Option[Aggregation[Genre]])
object Aggregations {
  val validAggregationRequests = List("workType", "genre")

  def isEmpty(a: Aggregations): Boolean =
    a.workType.isEmpty && a.genre.isEmpty

  def getFromJson[AggregationDataType](json: Json)(
    implicit decoder: Decoder[AggregationDataType])
    : Option[Aggregation[AggregationDataType]] = {
    val maybeEsAggregation = fromJson[EsAggregation](json.toString).toOption

    maybeEsAggregation.map(esAggregation => {
      val buckets = esAggregation.buckets.flatMap(esAggregationBucket =>
        fromJson[AggregationDataType](esAggregationBucket.key.toString()) map (
          data =>
            AggregationBucket(data, esAggregationBucket.doc_count)) toOption)

      Aggregation(buckets = buckets)
    })
  }

  def apply(jsonString: String): Option[Aggregations] = {
    val json = fromJson[Map[String, Json]](jsonString)

    json match {
      case Failure(_) => None
      case Success(jsonMap) =>
        jsonMap.size match {
          case 0 => None
          case _ =>
            val workType =
              jsonMap
                .get("workType")
                .flatMap(json => getFromJson[WorkType](json))
            val genre =
              jsonMap.get("genre").flatMap(json => getFromJson[Genre](json))

            Some(Aggregations(workType = workType, genre = genre))
        }
    }
  }
}

case class Aggregation[T](buckets: List[AggregationBucket[T]])
case class AggregationBucket[T](data: T, count: Int)

/**
  * We use these to convert the JSON into Elasticsearch case classes (not supplied via elastic4s)
  * and then convert them into our representations of aggregations.
  * This is to get around things like having property names like `doc_count` and `key`
  * The general format here is:
  *
  * {
  *   "buckets": {
  *     "key": {
  *       "this": "is",
  *       "structured": "content"
  *     },
  *     "doc_count" 1009
  *   }
  * }
  *
  * And we convert them to:
  * {
  *   "buckets": {
  *     "data": {
  *       "this": "is",
  *       "structured": "content"
  *     },
  *     "count" 1009
  *   }
  * }
  */
case class EsAggregation(buckets: List[EsAggregationBucket])
case class EsAggregationBucket(key: Json, doc_count: Int)
