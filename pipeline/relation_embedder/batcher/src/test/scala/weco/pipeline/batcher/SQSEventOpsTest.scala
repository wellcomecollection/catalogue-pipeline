package weco.pipeline.batcher

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.JavaConverters._

class SQSEventOpsTest extends AnyFunSpec with Matchers {
  import lib.SQSEventOps._

  describe("Using the implicit class SQSEventOps") {
    it("extracts paths from an SQSEvent") {
      val fakeMessage = new SQSMessage()
        fakeMessage.setBody("{\"Message\":\"A/C\"}")
      val fakeSQSEvent = new SQSEvent()
        fakeSQSEvent.setRecords(List(fakeMessage).asJava)

      val paths = fakeSQSEvent.extractPaths

      paths shouldBe List("A/C")
    }
  }
}
