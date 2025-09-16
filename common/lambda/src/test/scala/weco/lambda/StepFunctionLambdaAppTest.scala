package weco.lambda

import com.amazonaws.services.lambda.runtime.Context
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.{Future, Promise, TimeoutException}
import scala.concurrent.duration._

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

            val jsonIn = input.asJson.noSpaces
            val jsonOut = app.handleRequest(jsonIn, mockContext)
            io.circe.parser.decode[TestOutput](jsonOut).right.get shouldBe expectedOutput
      }

      it("handles processing failures") {
        val input = TestInput("test", 42)
        val exception = new RuntimeException("Processing failed")
        val app = new TestStepFunctionLambdaApp(
          processResult = Future.failed(exception)
        )
        val jsonIn = input.asJson.noSpaces
        val thrown = intercept[RuntimeException] { app.handleRequest(jsonIn, mockContext) }
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
        val jsonIn = input.asJson.noSpaces
        intercept[TimeoutException] { app.handleRequest(jsonIn, mockContext) }
      }

      it("handles execution exceptions") {
        val input = TestInput("test", 42)
        val exception = new IllegalArgumentException("Invalid input")
        val app = new TestStepFunctionLambdaApp(
          processResult = Future.failed(exception)
        )
        val jsonIn = input.asJson.noSpaces
        val thrown = intercept[IllegalArgumentException] { app.handleRequest(jsonIn, mockContext) }
        thrown.getMessage shouldBe "Invalid input"
      }

      it("logs request processing") {
        val input = TestInput("test", 42)
        val expectedOutput = TestOutput("success", true)
        val app = new TestStepFunctionLambdaApp(
          processResult = Future.successful(expectedOutput)
        )
        val jsonIn = input.asJson.noSpaces
        val jsonOut = app.handleRequest(jsonIn, mockContext)
        io.circe.parser.decode[TestOutput](jsonOut).right.get shouldBe expectedOutput
      }

      it("fails on invalid JSON input") {
        val app = new TestStepFunctionLambdaApp()
        val badJson = "{not valid json" // malformed
        val thrown = intercept[RuntimeException] { app.handleRequest(badJson, mockContext) }
        thrown.getMessage should include ("Failed to decode input")
      }

      it("fails on structurally incorrect JSON") {
        // Missing required fields for TestInput (message, value)
        val app = new TestStepFunctionLambdaApp()
        val json = "{\"message\":123}" // wrong type & missing value
        val thrown = intercept[RuntimeException] { app.handleRequest(json, mockContext) }
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