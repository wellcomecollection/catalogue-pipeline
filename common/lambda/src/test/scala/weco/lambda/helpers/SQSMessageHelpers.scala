package weco.lambda.helpers

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import weco.fixtures.RandomGenerators

import scala.collection.JavaConverters._

trait SQSMessageHelpers extends RandomGenerators {
  def createSQSEventsWithId(
    messages: List[(String, String)],
    wrapMessage: Boolean = true
  ): SQSEvent = {
    val sqsMessages = messages.map {
      case (body, messageId) =>
        val message = new SQSMessage()
        message.setMessageId(messageId)

        if (wrapMessage) {
          message.setBody(s"""{"Message": "$body"}""")
        } else {
          message.setBody(body)
        }

        message
    }
    val sqsEvent = new SQSEvent()

    sqsEvent.setRecords(sqsMessages.asJava)
    sqsEvent
  }

  def createSQSEvents(
    messages: List[String],
    wrapMessage: Boolean = true
  ): SQSEvent = {
    val messageTupleWithId = messages.map {
      message =>
        (message, randomUUID.toString)
    }
    createSQSEventsWithId(messageTupleWithId, wrapMessage)
  }
}
