package weco.pipeline.relation_embedder.lib

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import ujson.Value
import weco.json.JsonUtil.fromJson
import weco.pipeline.relation_embedder.models.Batch
import weco.json.JsonUtil._

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
  implicit class ExtractBatchFromSqsEvent(event: SQSEvent) {
    def extractBatches: List[Batch] =
      event.getRecords.asScala.toList.flatMap(extractBatchFromMessage)

    private def extractBatchFromMessage(message: SQSMessage): Option[Batch] =
      ujson.read(message.getBody).obj.get("Message").flatMap {
        value: Value => fromJson[Batch](value.str).toOption
      }
  }
}
