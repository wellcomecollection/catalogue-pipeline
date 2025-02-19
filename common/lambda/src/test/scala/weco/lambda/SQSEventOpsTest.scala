package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import weco.json.JsonUtil._

import scala.util.{Failure, Success}

class SQSEventOpsTest extends AnyFunSpec with Matchers {

  import SQSEventOps._

  def createSQSMessage(
    body: String,
    messageId: String
  ): SQSMessage = {
    val message = new SQSMessage()

    message.setMessageId(messageId)
    message.setBody(body)
    message
  }

  def createSQSEvents(messages: List[(String, String)]): SQSEvent = {
    val sqsMessages = messages.map {
      case (body, messageId) =>
        createSQSMessage(body, messageId)
    }
    val sqsEvent = new SQSEvent()

    sqsEvent.setRecords(sqsMessages.asJava)
    sqsEvent
  }

  describe("Using the implicit class SQSEventOps") {
    describe("extract") {
      it("extracts values from an SQSEvent where the message is a String") {
        val fakeMessage = new SQSMessage()
        fakeMessage.setBody("{\"Message\":\"A/C\"}")
        val fakeSQSEvent = new SQSEvent()
        fakeSQSEvent.setRecords(List(fakeMessage).asJava)

        val paths = fakeSQSEvent.extract[String]()

        paths shouldBe List("A/C")
      }

      case class TestMessage(value: String)

      it(
        "extracts values from an SQSEvent where the message is a JSON object"
      ) {
        val fakeMessage = new SQSMessage()
        fakeMessage.setBody("{\"Message\":\"{\\\"value\\\": \\\"A/C\\\"}\"}")
        val fakeSQSEvent = new SQSEvent()
        fakeSQSEvent.setRecords(List(fakeMessage).asJava)

        val paths = fakeSQSEvent.extract[TestMessage]()

        paths shouldBe List(TestMessage("A/C"))
      }

      it("extracts multiple values from an SQSEvent") {
        val fakeMessage1 = new SQSMessage()
        fakeMessage1.setBody("{\"Message\":\"A/C\"}")
        val fakeMessage2 = new SQSMessage()
        fakeMessage2.setBody("{\"Message\":\"A/E\"}")
        val fakeSQSEvent = new SQSEvent()
        fakeSQSEvent.setRecords(List(fakeMessage1, fakeMessage2).asJava)

        val paths = fakeSQSEvent.extract[String]()

        paths shouldBe List("A/C", "A/E")
      }

      it(
        "extracts values from an SQSEvent where the message is a JSON object with multiple fields, only taking the ones we want"
      ) {
        val fakeMessage = new SQSMessage()
        fakeMessage.setBody(
          "{\"Message\":\"{\\\"value\\\": \\\"A/C\\\", \\\"other\\\": \\\"D/E\\\"}\"}"
        )
        val fakeSQSEvent = new SQSEvent()
        fakeSQSEvent.setRecords(List(fakeMessage).asJava)

        val paths = fakeSQSEvent.extract[TestMessage]()

        paths shouldBe List(TestMessage("A/C"))
      }
    }

    describe("extractLambdaEvent") {
      it("extracts values from an SQSEvent where the message is a String") {
        val fakeMessageString = "A/C"
        val fakeMessageJson = s"""{"Message":"$fakeMessageString"}"""
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List((fakeMessageJson, fakeMessageId))
        )

        val paths = fakeSQSEvent.extractLambdaEvents[String]()

        paths shouldBe List(
          Success(
            SQSLambdaMessage(fakeMessageId, fakeMessageString)
          )
        )
      }

      case class TestMessage(value: String)

      it(
        "extracts values from an SQSEvent where the message is a JSON object"
      ) {
        val fakeMessageInnerJson = """{\"value\": \"A/C\"}"""
        val fakeMessageJson = s"""{"Message":"$fakeMessageInnerJson"}"""
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List((fakeMessageJson, fakeMessageId))
        )

        val paths = fakeSQSEvent.extractLambdaEvents[TestMessage]()

        paths shouldBe List(
          Success(
            SQSLambdaMessage(fakeMessageId, TestMessage("A/C"))
          )
        )
      }

      it("extracts multiple values from an SQSEvent") {
        val fakeMessageStrings = List("A/C", "A/E", "A/F")
        val fakeMessages = fakeMessageStrings.map {
          m =>
            s"""{"Message":"$m"}"""
        }
        val fakeIDs =
          fakeMessages.map(_ => java.util.UUID.randomUUID().toString)
        val fakeSQSEvent = createSQSEvents(
          fakeMessages.zip(fakeIDs)
        )

        val paths = fakeSQSEvent.extractLambdaEvents[String]()

        val expectedPaths = fakeMessageStrings.map {
          message =>
            val id = fakeIDs(fakeMessageStrings.indexOf(message))
            Success(SQSLambdaMessage(id, message))
        }

        paths shouldBe expectedPaths
      }

      it(
        "extracts values from an SQSEvent where the message is a JSON object with multiple fields, only taking the ones we want"
      ) {
        val fakeMessageInnerJson =
          """{\"value\": \"A/C\", \"other\": \"D/E\"}"""
        val fakeMessageJson = s"""{"Message":"$fakeMessageInnerJson"}"""
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List((fakeMessageJson, fakeMessageId))
        )

        val paths = fakeSQSEvent.extractLambdaEvents[TestMessage]()

        paths shouldBe List(
          Success(
            SQSLambdaMessage(fakeMessageId, TestMessage("A/C"))
          )
        )
      }

      // test failure mode where the outer message is not a JSON object
      it(
        "fails to extract values from an SQSEvent where the message is not a JSON object"
      ) {
        val fakeMessageBrokenJson = "invalid json"
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List((fakeMessageBrokenJson, fakeMessageId))
        )

        val paths = fakeSQSEvent.extractLambdaEvents[String]()

        paths.length shouldBe 1
        paths.head shouldBe a[Failure[_]]
        paths.head.failed.get.getMessage should startWith(
          "Failed to parse message body"
        )
      }

      // test failure mode where outer message is JSON, but doesn't contain a "Message" field
      it(
        "fails to extract values from an SQSEvent where the message is a JSON object without a Message field"
      ) {
        val fakeMessageInnerJson = """{\"value\": \"A/C\"}"""
        val fakeMessageJson = s"""{"NotMessage":"$fakeMessageInnerJson"}"""
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List((fakeMessageJson, fakeMessageId))
        )

        val paths = fakeSQSEvent.extractLambdaEvents[TestMessage]()

        paths.length shouldBe 1
        paths.head shouldBe a[Failure[_]]
        paths.head.failed.get.getMessage should startWith(
          "Failed to extract Message object, incorrect format?"
        )
      }

      // test failure mode where inner message is not a JSON object
      it(
        "fails to extract values from an SQSEvent where the message is a JSON object with a non-JSON Message field"
      ) {
        val fakeMessageInnerJson = "not json"
        val fakeMessageJson = s"""{"Message":"$fakeMessageInnerJson"}"""
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List((fakeMessageJson, fakeMessageId))
        )

        val paths = fakeSQSEvent.extractLambdaEvents[TestMessage]()

        paths.length shouldBe 1
        paths.head shouldBe a[Failure[_]]
        paths.head.failed.get.getMessage should startWith(
          "Failed to decode inner message"
        )
      }

      // mixed success failure mode where one message is valid and one is not
      it(
        "fails to extract values from an SQSEvent where one message is valid and one is not"
      ) {
        val fakeMessageInnerJson = """{\"value\": \"A/C\"}"""
        val fakeMessageJson = s"""{"Message":"$fakeMessageInnerJson"}"""
        val fakeMessageBrokenJson = "invalid json"
        val fakeMessageId = java.util.UUID.randomUUID().toString

        val fakeSQSEvent = createSQSEvents(
          List(
            (fakeMessageJson, fakeMessageId),
            (fakeMessageBrokenJson, fakeMessageId)
          )
        )

        val paths = fakeSQSEvent.extractLambdaEvents[TestMessage]()

        paths.length shouldBe 2
        paths.head shouldBe a[Success[_]]
        paths(1) shouldBe a[Failure[_]]
        paths(1).failed.get.getMessage should startWith(
          "Failed to parse message body"
        )
      }
    }
  }
}
