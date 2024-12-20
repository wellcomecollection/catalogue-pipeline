package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import io.circe.Decoder
import ujson.Value
import weco.json.JsonUtil.fromJson

import scala.collection.JavaConverters._

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
    def extract[T]()(implicit decoder: Decoder[T]) =
      event.getRecords.asScala.toList.flatMap(extractFromMessage[T](_))

    private def extractFromMessage[T](message: SQSMessage)(implicit decoder: Decoder[T]): Option[T] =
      ujson.read(message.getBody).obj.get("Message").flatMap {
        value: Value => fromJson[T](value.str).toOption
      }
  }
}
