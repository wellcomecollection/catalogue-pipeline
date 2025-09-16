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

          val memoryDownstream = new MemorySNSDownstream()
          withIdMinterStepFunctionLambda(
            identifiersTableConfig,
            inputIndex,
            outputIndex,
            memoryDownstream
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

              // Verify the work was stored correctly (key = canonical ID)
              outputIndex should have size 1
              val canonicalId = outputIndex.keys.head
              val identifiedWork = outputIndex.values.head
              identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier

              // We invoked the lambda 3 times; expect 3 downstream notifications, all for the canonical ID
              val msgs = memoryDownstream.msgSender.messages.map(_.body)
              msgs should have size 3
              msgs.distinct should contain only canonicalId
          }
      }
    }

    it("mints an identifier for an invisible work") {
      withIdentifiersTable {
        identifiersTableConfig =>
          val work = sourceWork().invisible()
          val inputIndex = createIndex(List(work))
          val outputIndex = mutable.Map.empty[String, Work[Identified]]

          val memoryDownstream = new MemorySNSDownstream()
          withIdMinterStepFunctionLambda(
            identifiersTableConfig,
            inputIndex,
            outputIndex,
            memoryDownstream
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
              val canonicalId = outputIndex.keys.head
              // Expect exactly one downstream notification for the canonical ID
              memoryDownstream.msgSender.messages.map(
                _.body
              ) should contain only canonicalId
          }
      }
    }

  }
}
