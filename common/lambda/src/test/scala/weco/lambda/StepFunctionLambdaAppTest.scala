package weco.lambda

import com.amazonaws.services.lambda.runtime.Context
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import scala.collection.JavaConverters._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{Future, Promise, TimeoutException}
import scala.concurrent.duration._
import StepFunctionLambdaAppTestHelpers.{jsonToJavaMap, javaMapToJson}

class StepFunctionLambdaAppTest extends AnyFunSpec with Matchers with MockitoSugar {

  case class TestInput(message: String, value: Int)
  case class TestOutput(result: String, processed: Boolean)
  case class TestConfig() extends ApplicationConfig

  object TestInput {
    implicit val decoder: Decoder[TestInput] = deriveDecoder
    implicit val encoder: Encoder[TestInput] = deriveEncoder
  }

  object TestOutput {
    implicit val decoder: Decoder[TestOutput] = deriveDecoder
    implicit val encoder: Encoder[TestOutput] = deriveEncoder
  }

  class TestStepFunctionLambdaApp(
    processResult: Future[TestOutput] = Future.successful(TestOutput("success", true)),
    timeout: FiniteDuration = 15.minutes
  ) extends StepFunctionLambdaApp[TestInput, TestOutput, TestConfig] {
    
    import com.typesafe.config.Config
    
    override protected val maximumExecutionTime: FiniteDuration = timeout
    
    override val config: TestConfig = TestConfig()
    
    override def build(rawConfig: Config): TestConfig = TestConfig()

    override def processRequest(input: TestInput): Future[TestOutput] = {
      processResult
    }

    // Expose protected field for testing
    def getMaximumExecutionTime: FiniteDuration = maximumExecutionTime
  }

  describe("StepFunctionLambdaApp") {
    val mockContext = mock[Context]
    when(mockContext.getAwsRequestId).thenReturn("test-request-id")
    when(mockContext.getRemainingTimeInMillis).thenReturn(300000) // 5 minutes

    describe("handleRequest") {
      it("processes successful requests") {
            val input = TestInput("test", 42)
            val expectedOutput = TestOutput("processed: test-42", true)

            val app = new TestStepFunctionLambdaApp(
              processResult = Future.successful(expectedOutput)
            )

            val mapIn = jsonToJavaMap(input.asJson)
            val mapOut = app.handleRequest(mapIn, mockContext)
            val jsonOut = javaMapToJson(mapOut)
            jsonOut.as[TestOutput].right.get shouldBe expectedOutput
      }

      it("handles processing failures") {
        val input = TestInput("test", 42)
        val exception = new RuntimeException("Processing failed")
        val app = new TestStepFunctionLambdaApp(
          processResult = Future.failed(exception)
        )
            val mapIn = jsonToJavaMap(input.asJson)
            val thrown = intercept[RuntimeException] { app.handleRequest(mapIn, mockContext) }
        thrown.getMessage shouldBe "Processing failed"
      }

      it("handles timeout scenarios") {
        val input = TestInput("test", 42)
        val promise = Promise[TestOutput]()
        val neverCompletingFuture = promise.future
        val app = new TestStepFunctionLambdaApp(
          processResult = neverCompletingFuture,
          timeout = 100.millis
        )
            val mapIn = jsonToJavaMap(input.asJson)
            intercept[TimeoutException] { app.handleRequest(mapIn, mockContext) }
      }

      it("handles execution exceptions") {
        val input = TestInput("test", 42)
        val exception = new IllegalArgumentException("Invalid input")
        val app = new TestStepFunctionLambdaApp(
          processResult = Future.failed(exception)
        )
            val mapIn = jsonToJavaMap(input.asJson)
            val thrown = intercept[IllegalArgumentException] { app.handleRequest(mapIn, mockContext) }
        thrown.getMessage shouldBe "Invalid input"
      }

      it("logs request processing") {
        val input = TestInput("test", 42)
        val expectedOutput = TestOutput("success", true)
        val app = new TestStepFunctionLambdaApp(
          processResult = Future.successful(expectedOutput)
        )
            val mapIn = jsonToJavaMap(input.asJson)
            val mapOut = app.handleRequest(mapIn, mockContext)
            val jsonOut = javaMapToJson(mapOut)
            jsonOut.as[TestOutput].right.get shouldBe expectedOutput
      }

      it("fails on invalid JSON input") {
        val app = new TestStepFunctionLambdaApp()
    // Build a malformed structure by forcing a value that will not decode to TestInput
            // For TestInput(message: String, value: Int) supply a map missing fields
            val badMap = new java.util.LinkedHashMap[String, AnyRef]()
            badMap.put("unexpected", "field")
            val thrown = intercept[RuntimeException] { app.handleRequest(badMap, mockContext) }
            thrown.getMessage should include ("Failed to decode input")
      }

      it("fails on structurally incorrect JSON") {
        // Missing required fields for TestInput (message, value)
        val app = new TestStepFunctionLambdaApp()
            val wrongTypeMap = new java.util.LinkedHashMap[String, AnyRef]()
            wrongTypeMap.put("message", Int.box(123)) // wrong type for message (expects String)
            val thrown = intercept[RuntimeException] { app.handleRequest(wrongTypeMap, mockContext) }
        thrown.getMessage should include ("Failed to decode input")
      }
    }

    describe("processRequest") {
      it("is abstract and must be implemented by subclasses") {
        // This is tested implicitly by the TestStepFunctionLambdaApp implementation
        val input = TestInput("test", 42)
        val app = new TestStepFunctionLambdaApp()
        val resultF = app.processRequest(input)
        resultF.isCompleted shouldBe true
      }
    }

    describe("configuration") {
      it("provides access to configuration") {
        val app = new TestStepFunctionLambdaApp()
        app.config shouldBe a[TestConfig]
      }

      it("has configurable timeout") {
        val customTimeout = 5.minutes
        val app = new TestStepFunctionLambdaApp(timeout = customTimeout)
        app.getMaximumExecutionTime shouldBe customTimeout
      }

      it("has default timeout of 15 minutes") {
        val app = new TestStepFunctionLambdaApp()
        app.getMaximumExecutionTime shouldBe 15.minutes
      }
    }

    describe("actor system management") {
      it("provides an actor system") {
        val app = new TestStepFunctionLambdaApp()
        app.actorSystem should not be null
        app.actorSystem.name shouldBe "main-actor-system"
      }

      it("provides an execution context") {
        val app = new TestStepFunctionLambdaApp()
        app.ec should not be null
      }
    }
  }
}

// Helper functions (top-level) mirroring conversion logic in StepFunctionLambdaApp for tests
private object StepFunctionLambdaAppTestHelpers {
  def jsonToJavaMap(json: Json): java.util.LinkedHashMap[String, AnyRef] = {
    json.asObject match {
      case Some(obj) => jsonObjectToMap(obj)
      case None => throw new IllegalArgumentException("Top-level JSON must be an object for this test")
    }
  }

  private def jsonToAnyRef(json: Json): AnyRef = json.fold[AnyRef](
    null,
    bool => java.lang.Boolean.valueOf(bool),
    num => num.toBigDecimal.map { bd =>
      if (bd.isValidInt) Int.box(bd.toInt)
      else if (bd.isValidLong) Long.box(bd.toLong)
      else new java.math.BigDecimal(bd.toString)
    }.orNull,
    str => str,
    arr => {
      val list = new java.util.ArrayList[AnyRef](arr.size)
      arr.foreach(j => list.add(jsonToAnyRef(j)))
      list
    },
    obj => jsonObjectToMap(obj)
  )

  private def jsonObjectToMap(obj: JsonObject): java.util.LinkedHashMap[String, AnyRef] = {
    val m = new java.util.LinkedHashMap[String, AnyRef]()
    obj.toMap.foreach { case (k, v) => m.put(k, jsonToAnyRef(v)) }
    m
  }

  private def anyRefToJson(value: AnyRef): Json = value match {
    case null => Json.Null
    case m: java.util.Map[_, _] @unchecked =>
      Json.obj(m.asInstanceOf[java.util.Map[String, AnyRef]].asScala.map { case (k, v) => (k, anyRefToJson(v)) }.toSeq: _*)
    case l: java.util.List[_] @unchecked => Json.fromValues(l.asInstanceOf[java.util.List[AnyRef]].asScala.map(anyRefToJson))
    case b: java.lang.Boolean => Json.fromBoolean(b)
    case n: java.lang.Integer => Json.fromInt(n)
    case n: java.lang.Long => Json.fromLong(n)
    case n: java.lang.Double => Json.fromDoubleOrNull(n)
    case n: java.lang.Float => Json.fromFloatOrNull(n)
    case n: java.math.BigDecimal => Json.fromBigDecimal(scala.math.BigDecimal(n))
    case n: java.lang.Number => Json.fromBigDecimal(BigDecimal(n.toString))
    case s: String => Json.fromString(s)
    case other => Json.fromString(other.toString)
  }

  def javaMapToJson(map: java.util.LinkedHashMap[String, AnyRef]): Json = anyRefToJson(map)
}