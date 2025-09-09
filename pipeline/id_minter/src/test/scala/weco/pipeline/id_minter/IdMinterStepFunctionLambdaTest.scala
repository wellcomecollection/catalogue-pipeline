package weco.pipeline.id_minter

import com.typesafe.config.Config
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda.ApplicationConfig
import weco.pipeline.id_minter.models.{
  StepFunctionMintingFailure,
  StepFunctionMintingRequest
}

import scala.concurrent.{ExecutionContext, Future}

class IdMinterStepFunctionLambdaTest extends AnyFunSpec with Matchers with ScalaFutures with WorkGenerators {

  case class TestConfig() extends ApplicationConfig

  class TestIdMinterStepFunctionLambda(
    testProcessor: MintingRequestProcessor
  ) extends IdMinterStepFunctionLambda[TestConfig] {
    
    override val config: TestConfig = TestConfig()
    override def build(rawConfig: Config): TestConfig = TestConfig()
    
    override protected val processor: MintingRequestProcessor = testProcessor
  }

  // Helper to create a mock processor that succeeds for given source IDs
  def createMockProcessor(successfulSourceIds: List[String], failedSourceIds: List[String] = List.empty): MintingRequestProcessor = {
    new MintingRequestProcessor(null, null)(ExecutionContext.global) {
      override def process(sourceIdentifiers: Seq[String]): Future[MintingResponse] = {
        val successes = sourceIdentifiers.filter(successfulSourceIds.contains).map(_ => "minted-id") // Canonical IDs don't matter for Step Function response
        val failures = sourceIdentifiers.filter(id => failedSourceIds.contains(id) || !successfulSourceIds.contains(id))
        Future.successful(MintingResponse(successes, failures))
      }
    }
  }

  describe("IdMinterStepFunctionLambda") {
    describe("processRequest") {
      it("handles successful minting and indexing") {
        val sourceIds = List("sierra-123", "miro-456")
        val mockProcessor = createMockProcessor(successfulSourceIds = sourceIds)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(sourceIds, Some("test-job"))
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes should have size 2
        result.successes should contain allOf("sierra-123", "miro-456")
        result.failures shouldBe empty
        result.jobId shouldBe Some("test-job")
      }

      it("handles minting failures") {
        val successfulId = "sierra-123"
        val failedSourceId = "sierra-failed-123"
        val sourceIds = List(successfulId, failedSourceId)
        val mockProcessor = createMockProcessor(successfulSourceIds = List(successfulId), failedSourceIds = List(failedSourceId))
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(sourceIds, Some("test-job"))
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes should have size 1
        result.successes should contain(successfulId)
        result.failures should have size 1
        result.failures should contain(StepFunctionMintingFailure(failedSourceId, s"Failed to mint ID for $failedSourceId"))
        result.jobId shouldBe Some("test-job")
      }

      it("handles indexing failures") {
        val sourceIds = List("sierra-123", "miro-456")
        val mockProcessor = createMockProcessor(successfulSourceIds = List.empty, failedSourceIds = sourceIds) // All fail
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(sourceIds, Some("test-job"))
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes shouldBe empty
        result.failures should have size 2
        result.failures.map(_.sourceIdentifier) should contain allOf("sierra-123", "miro-456")
        result.jobId shouldBe Some("test-job")
      }

      it("handles empty source identifiers") {
        val mockProcessor = createMockProcessor(successfulSourceIds = List.empty)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(List.empty, Some("test-job"))
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes shouldBe empty
        result.failures should have size 1
        result.failures.head.error should include("sourceIdentifiers cannot be empty")
        result.jobId shouldBe Some("test-job")
      }

      it("handles invalid input validation") {
        val mockProcessor = createMockProcessor(successfulSourceIds = List.empty)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(List("sierra-123", ""), Some("test-job"))
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes shouldBe empty
        result.failures should have size 1
        result.failures.head.error should include("sourceIdentifiers cannot contain empty strings")
        result.jobId shouldBe Some("test-job")
      }

      it("handles requests without jobId") {
        val sourceIds = List("sierra-123")
        val mockProcessor = createMockProcessor(successfulSourceIds = sourceIds)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(sourceIds, None)
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes should have size 1
        result.successes should contain("sierra-123")
        result.failures shouldBe empty
        result.jobId shouldBe None
      }

      it("handles batch size validation") {
        val largeSourceIds = (1 to 101).map(i => s"sierra-$i").toList
        val mockProcessor = createMockProcessor(successfulSourceIds = List.empty)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(largeSourceIds, Some("test-job"))
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes shouldBe empty
        result.failures should have size 1
        result.failures.head.error should include("sourceIdentifiers cannot contain more than 100 items")
        result.jobId shouldBe Some("test-job")
      }
    }
  }
}