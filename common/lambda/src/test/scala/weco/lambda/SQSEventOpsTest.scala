package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import weco.json.JsonUtil._

class SQSEventOpsTest extends AnyFunSpec with Matchers {

  import SQSEventOps._

  describe("Using the implicit class SQSEventOps") {
    it("extracts values from an SQSEvent where the message is a String") {
      val fakeMessage = new SQSMessage()
      fakeMessage.setBody("{\"Message\":\"A/C\"}")
      val fakeSQSEvent = new SQSEvent()
      fakeSQSEvent.setRecords(List(fakeMessage).asJava)

      val paths = fakeSQSEvent.extract[String]()

      paths shouldBe List("A/C")
    }

    case class TestMessage(value: String)

    it("extracts values from an SQSEvent where the message is a JSON object") {
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
}
