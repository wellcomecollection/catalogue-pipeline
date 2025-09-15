package weco.pipeline.id_minter

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.id_minter.models.StepFunctionMintingRequest
import weco.pipeline_storage.Indexer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class StepFunctionMainTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WorkGenerators {

  class MockIndexer(
    applyResponse: Either[Seq[Work[Identified]], Seq[Work[Identified]]]
  ) extends Indexer[Work[Identified]] {

    override def init(): Future[Unit] = Future.successful(Unit)

    override def apply(
      documents: Seq[Work[Identified]]
    ): Future[Either[Seq[Work[Identified]], Seq[Work[Identified]]]] =
      Future.successful(applyResponse)
  }

  class MockMinter(
    applyResponse: Iterable[Either[String, Work[Identified]]]
  ) extends IdListMinter {

    override def processSourceIds(identifiers: Seq[String])(
      implicit ec: ExecutionContext
    ): Future[Iterable[Either[String, Work[Identified]]]] =
      Future.successful(applyResponse)
  }

  case class TestConfig() extends weco.lambda.ApplicationConfig

  describe("StepFunctionMain") {
    it("processes a valid Step Function request with successful minting") {
      val works = identifiedWorks(2)
      val sourceIds = works.map(_.sourceIdentifier.toString)
      
      val testProcessor = new MintingRequestProcessor(
        minter = new MockMinter(works.map(Right(_))),
        workIndexer = new MockIndexer(Right(works))
      )

      val testMain = new IdMinterStepFunctionLambda[TestConfig] {
        override val config: TestConfig = TestConfig()
        override def build(rawConfig: com.typesafe.config.Config): TestConfig = TestConfig()
        
        override protected val processor = testProcessor
      }

  val request = StepFunctionMintingRequest(sourceIds, "test-job-001")
      val result = testMain.processRequest(request).futureValue

      result.successes should have size 2
      result.successes should contain allOf(sourceIds.head, sourceIds.tail.head)
      result.failures shouldBe empty
  result.jobId shouldBe "test-job-001"
    }

    it("handles mixed success and failure scenarios") {
      val successfulWork = identifiedWork()
      val failedSourceId = "failed-source-123"
      val sourceIds = List(successfulWork.sourceIdentifier.toString, failedSourceId)
      
      val testProcessor = new MintingRequestProcessor(
        minter = new MockMinter(Seq(Right(successfulWork), Left(failedSourceId))),
        workIndexer = new MockIndexer(Right(Seq(successfulWork)))
      )

      val testMain = new IdMinterStepFunctionLambda[TestConfig] {
        override val config: TestConfig = TestConfig()
        override def build(rawConfig: com.typesafe.config.Config): TestConfig = TestConfig()
        
        override protected val processor = testProcessor
      }

  val request = StepFunctionMintingRequest(sourceIds, "test-job-002")
      val result = testMain.processRequest(request).futureValue

      result.successes should have size 1
      result.successes should contain(successfulWork.sourceIdentifier.toString)
      result.failures should have size 1
      result.failures.head.sourceIdentifier shouldBe failedSourceId
  result.jobId shouldBe "test-job-002"
    }

    it("handles indexing failures") {
      val works = identifiedWorks(2)
      val sourceIds = works.map(_.sourceIdentifier.toString)
      val failedWork = works.head
      val successfulWork = works.tail.head
      
      val testProcessor = new MintingRequestProcessor(
        minter = new MockMinter(works.map(Right(_))),
        workIndexer = new MockIndexer(Left(Seq(failedWork))) // First work fails indexing
      )

      val testMain = new IdMinterStepFunctionLambda[TestConfig] {
        override val config: TestConfig = TestConfig()
        override def build(rawConfig: com.typesafe.config.Config): TestConfig = TestConfig()
        
        override protected val processor = testProcessor
      }

  val request = StepFunctionMintingRequest(sourceIds, "test-job-003")
      val result = testMain.processRequest(request).futureValue

      result.successes should have size 1
      result.successes should contain(successfulWork.sourceIdentifier.toString)
      result.failures should have size 1
      result.failures.head.sourceIdentifier shouldBe failedWork.sourceIdentifier.toString
  result.jobId shouldBe "test-job-003"
    }

    it("validates input parameters correctly") {
      val testProcessor = new MintingRequestProcessor(
        minter = new MockMinter(Seq.empty),
        workIndexer = new MockIndexer(Right(Seq.empty))
      )

      val testMain = new IdMinterStepFunctionLambda[TestConfig] {
        override val config: TestConfig = TestConfig()
        override def build(rawConfig: com.typesafe.config.Config): TestConfig = TestConfig()
        
        override protected val processor = testProcessor
      }

    // Empty list now returns an empty success/failure response (no error)
    val emptyRequest = StepFunctionMintingRequest(List.empty, "test-job")
    val emptyResult = testMain.processRequest(emptyRequest).futureValue
    emptyResult.successes shouldBe empty
    emptyResult.failures shouldBe empty

      // Removed: batch size limit validation no longer enforced
    }

    it("fails validation if jobId is empty") {
      val testProcessor = new MintingRequestProcessor(
        minter = new MockMinter(Seq.empty),
        workIndexer = new MockIndexer(Right(Seq.empty))
      )

      val testMain = new IdMinterStepFunctionLambda[TestConfig] {
        override val config: TestConfig = TestConfig()
        override def build(rawConfig: com.typesafe.config.Config): TestConfig = TestConfig()
        override protected val processor = testProcessor
      }

      val request = StepFunctionMintingRequest(List("sierra-1"), "   ")
      val result = testMain.processRequest(request).futureValue
      result.successes shouldBe empty
      result.failures should have size 1
      result.failures.head.error should include ("jobId cannot be empty")
    }
  }
}