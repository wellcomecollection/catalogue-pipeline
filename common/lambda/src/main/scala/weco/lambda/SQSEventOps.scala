package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import io.circe.Decoder
import ujson.Value
import weco.json.JsonUtil.fromJson

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object SQSEventOps {

  /** Messages consumed by Lambda from SQS are taken from a queue populated by
    * an SNS topic. The actual message we are interested in is a String
    * containing the path. However, the matryoshka-like nature of these things
    * means the lambda receives
    *   - an event containing
    *   - a `Records` list, each Record containing
    *   - an SQS Message with a JSON body containing
    *   - an SNS notification containing
    *   - a `Message`, which is the actual content we want
    */
  implicit class ExtractTFromSqsEvent(event: SQSEvent) {
    def extract[T]()(implicit decoder: Decoder[T], ct: ClassTag[T]): List[T] =
      event.getRecords.asScala.toList.flatMap(extractFromMessage[T](_))

    def extractMap[T]()(
      implicit decoder: Decoder[T],
      ct: ClassTag[T]
    ): Map[T, Seq[SQSMessage]] =
      event.getRecords.asScala.toList
        .flatMap(
          message =>
            extractFromMessage[T](message) match {
              case None        => None
              case Some(value) => Some(value -> message)
            }
        )
        .groupBy(_._1)
        .map {
          case (k: T, v: Seq[(T, SQSMessage)]) => k -> v.map(_._2)
        }

    private def extractFromMessage[T](
      message: SQSMessage
    )(implicit decoder: Decoder[T], ct: ClassTag[T]): Option[T] =
      ujson.read(message.getBody).obj.get("Message").flatMap {
        value: Value =>
          {
            ct.runtimeClass match {
              case c if c == classOf[String] => Some(value.str.asInstanceOf[T])
              case _                         => fromJson[T](value.str).toOption
            }
          }
      }
  }
}
