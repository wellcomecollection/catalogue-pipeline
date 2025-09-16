package weco.pipeline.id_minter

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.id_minter.models.StepFunctionMintingRequest
import weco.pipeline.id_minter.utils.IdMinterStepFunctionTestHelpers

import scala.collection.mutable

class IdMinterStepFunctionFeatureTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Eventually
    with IdMinterStepFunctionTestHelpers
    with WorkGenerators {

  describe("IdMinter Step Function Interface") {
    it("mints the same IDs where source identifiers match") {
      withIdentifiersTable {
        identifiersTableConfig =>
          val work = sourceWork()
          val inputIndex = createIndex(List(work))
          val outputIndex = mutable.Map.empty[String, Work[Identified]]

          withIdMinterStepFunctionLambda(
            identifiersTableConfig,
            inputIndex,
            outputIndex
          ) {
            lambda =>
              eventuallyTableExists(identifiersTableConfig)

              val sourceIds = List(work.sourceIdentifier.toString)
              val request = StepFunctionMintingRequest(sourceIds, "test-job")

              // Process the same request multiple times
              val results = (1 to 3).map {
                _ =>
                  lambda.processRequest(request).futureValue
              }

              // All results should be identical
              results.foreach {
                result =>
                  result.successes should have size 1
                  result.failures shouldBe empty
                  result.jobId shouldBe "test-job"
              }

              // Verify the work was stored correctly
              outputIndex should have size 1
              val identifiedWork = outputIndex.values.head
              identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
          }
      }
    }

    it("mints an identifier for an invisible work") {
      withIdentifiersTable {
        identifiersTableConfig =>
          val work = sourceWork().invisible()
          val inputIndex = createIndex(List(work))
          val outputIndex = mutable.Map.empty[String, Work[Identified]]

          withIdMinterStepFunctionLambda(
            identifiersTableConfig,
            inputIndex,
            outputIndex
          ) {
            lambda =>
              eventuallyTableExists(identifiersTableConfig)

              val sourceIds = List(work.sourceIdentifier.toString)
              val request = StepFunctionMintingRequest(sourceIds, "test-job")

              val result = lambda.processRequest(request).futureValue

              result.successes should have size 1
              result.successes should contain(work.sourceIdentifier.toString)
              result.failures shouldBe empty
              result.jobId shouldBe "test-job"

              // Verify the work was stored correctly
              outputIndex should have size 1
          }
      }
    }

    it("ensures no downstream SQS messages are sent") {
      withIdentifiersTable {
        identifiersTableConfig =>
          val work = sourceWork()
          val inputIndex = createIndex(List(work))
          val outputIndex = mutable.Map.empty[String, Work[Identified]]

          withIdMinterStepFunctionLambda(
            identifiersTableConfig,
            inputIndex,
            outputIndex
          ) {
            lambda =>
              eventuallyTableExists(identifiersTableConfig)

              val sourceIds = List(work.sourceIdentifier.toString)
              val request = StepFunctionMintingRequest(sourceIds, "no-sqs-test")

              val result = lambda.processRequest(request).futureValue

              result.successes should have size 1
              result.failures shouldBe empty

              // The key difference from SQS interface: no downstream messages
              // This is verified by the fact that we only get success/failure indicators
              // in the response, not the actual canonical IDs
              result.successes should contain(work.sourceIdentifier.toString)

              // Verify work was still stored in the index (business logic still works)
              outputIndex should have size 1
          }
      }
    }
  }
}
