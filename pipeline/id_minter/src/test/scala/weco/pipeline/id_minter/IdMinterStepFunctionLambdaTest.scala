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
  it("handles all minting successes") {
        val sourceIds = List("sierra-123", "miro-456")
        val mockProcessor = createMockProcessor(successfulSourceIds = sourceIds)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
  val request = StepFunctionMintingRequest(sourceIds, "test-job")
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes should have size 2
        result.successes should contain allOf("sierra-123", "miro-456")
        result.failures shouldBe empty
  result.jobId shouldBe "test-job"
      }

      it("handles partial and complete minting failure scenarios") {
        val scenarios = Seq(
          // name, sourceIds, successfulIds
          ("partial failure", List("sierra-123", "miro-456"), List("sierra-123")),
          ("complete failure", List("sierra-123", "miro-456"), Nil)
        )

        scenarios.foreach { case (name, sourceIds, successfulIds) =>
          withClue(s"Scenario: $name => ") {
            val failedIds = sourceIds.diff(successfulIds)
            val mockProcessor = createMockProcessor(
              successfulSourceIds = successfulIds,
              failedSourceIds = failedIds
            )

            val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
            val request = StepFunctionMintingRequest(sourceIds, "test-job")

            val result = lambda.processRequest(request).futureValue

            result.successes.toSet shouldBe successfulIds.toSet
            result.failures.map(_.sourceIdentifier).toSet shouldBe failedIds.toSet

            if (failedIds.nonEmpty) {
              failedIds.foreach { fid =>
                result.failures should contain(
                  StepFunctionMintingFailure(fid, s"Failed to mint ID for $fid")
                )
              }
            } else {
              result.failures shouldBe empty
            }

            result.jobId shouldBe "test-job"
          }
        }
      }

      it("returns empty response (no failures) when source identifiers list is empty and does not invoke processor") {
        @volatile var invoked = false
        val mockProcessor = new MintingRequestProcessor(null, null)(ExecutionContext.global) {
          override def process(sourceIdentifiers: Seq[String]): Future[MintingResponse] = {
            invoked = true; Future.successful(MintingResponse(Nil, Nil))
          }
        }
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(Nil, "test-job")
        val result = lambda.processRequest(request).futureValue
        result.successes shouldBe empty
        result.failures shouldBe empty
        result.jobId shouldBe "test-job"
        invoked shouldBe false
      }

      it("handles invalid input validation") {
        val mockProcessor = createMockProcessor(successfulSourceIds = List.empty)
        
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
  val request = StepFunctionMintingRequest(List("sierra-123", ""), "test-job")
        
        val result = lambda.processRequest(request).futureValue
        
        result.successes shouldBe empty
        result.failures should have size 1
        result.failures.head.error should include("sourceIdentifiers cannot contain empty strings")
  result.jobId shouldBe "test-job"
      }

      it("fails validation when jobId is blank") {
        val sourceIds = List("sierra-123")
        val mockProcessor = createMockProcessor(successfulSourceIds = sourceIds)
        val lambda = new TestIdMinterStepFunctionLambda(mockProcessor)
        val request = StepFunctionMintingRequest(sourceIds, "   ")
        val result = lambda.processRequest(request).futureValue
        result.successes shouldBe empty
        result.failures should have size 1
        result.failures.head.error should include ("jobId cannot be empty")
      }
    }
  }
}