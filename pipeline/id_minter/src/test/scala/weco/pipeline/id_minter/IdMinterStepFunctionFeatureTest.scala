package weco.pipeline.id_minter

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.identifiers.IdState
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

  // Scenario: Multiple identical source identifiers (idempotency across repeated executions)
  describe("When there are multiple identical input source identifiers") {
    it("mints the same ID on repeated invocations of the same single-source request") {
      withIdentifiersTable { identifiersTableConfig =>
        val work = sourceWork()
        val inputIndex = createIndex(List(work))
        val outputIndex = mutable.Map.empty[String, Work[Identified]]
        val memoryDownstream = new MemorySNSDownstream()

        withIdMinterStepFunctionLambda(
          identifiersTableConfig,
          inputIndex,
          outputIndex,
          memoryDownstream
        ) { lambda =>
          eventuallyTableExists(identifiersTableConfig)

          val sourceIds = List(work.sourceIdentifier.toString)
            // We keep the request with a single identifier but invoke it multiple times to mirror
            // the SQS behaviour test that multiple identical upstream messages only create one record.
          val request = StepFunctionMintingRequest(sourceIds, "test-job")

            // Invoke three times
          val results = (1 to 3).map { _ => lambda.processRequest(request).futureValue }

          results.foreach { result =>
            result.successes should have size 1
            result.failures shouldBe empty
            result.jobId shouldBe "test-job"
          }

          // Only one identified work stored
          outputIndex should have size 1
          val canonicalId = outputIndex.keys.head
          val identifiedWork = outputIndex.values.head
          identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier

          // One downstream message per invocation (since we invoked 3 separate times)
          val msgs = memoryDownstream.msgSender.messages.map(_.body)
          msgs should have size 3
          msgs.distinct should contain only canonicalId
        }
      }
    }
  }

  // Scenario: Invisible work
  describe("When a work to be processed is marked as Invisible") {
    it("mints an identifier for an invisible work") {
      withIdentifiersTable { identifiersTableConfig =>
        val work = sourceWork().invisible()
        val inputIndex = createIndex(List(work))
        val outputIndex = mutable.Map.empty[String, Work[Identified]]
        val memoryDownstream = new MemorySNSDownstream()

        withIdMinterStepFunctionLambda(
          identifiersTableConfig,
          inputIndex,
          outputIndex,
          memoryDownstream
        ) { lambda =>
          eventuallyTableExists(identifiersTableConfig)
          val request = StepFunctionMintingRequest(List(work.sourceIdentifier.toString), "test-job")
          val result = lambda.processRequest(request).futureValue

          result.successes should have size 1
          result.successes should contain(work.sourceIdentifier.toString)
          result.failures shouldBe empty
          result.jobId shouldBe "test-job"

          outputIndex should have size 1
          val canonicalId = outputIndex.keys.head
          memoryDownstream.msgSender.messages.map(_.body) should contain only canonicalId
        }
      }
    }
  }

  // Scenario: Redirected work
  describe("When a work to be processed is marked as Redirected") {
    it("mints an identifier for a redirected work") {
      withIdentifiersTable { identifiersTableConfig =>
        val work = sourceWork().redirected(
          redirectTarget = IdState.Identifiable(sourceIdentifier = createSourceIdentifier)
        )
        val inputIndex = createIndex(List(work))
        val outputIndex = mutable.Map.empty[String, Work[Identified]]
        val memoryDownstream = new MemorySNSDownstream()

        withIdMinterStepFunctionLambda(
          identifiersTableConfig,
          inputIndex,
          outputIndex,
          memoryDownstream
        ) { lambda =>
          eventuallyTableExists(identifiersTableConfig)
          val request = StepFunctionMintingRequest(List(work.sourceIdentifier.toString), "test-job")
          val result = lambda.processRequest(request).futureValue

          result.successes should contain only work.sourceIdentifier.toString
          result.failures shouldBe empty
          outputIndex should have size 1
          val canonicalId = outputIndex.keys.head
          memoryDownstream.msgSender.messages.map(_.body) should contain only canonicalId
        }
      }
    }
  }

  // Scenario: Partial success when some source identifiers are missing upstream
  describe("When not all source identifiers correspond to a Work in the upstream index") {
    it("returns successes and failures appropriately, only minting for existing works") {
      withIdentifiersTable { identifiersTableConfig =>
        val work = sourceWork()
        val missingId = "this_work_id_does_not_exist"
        val inputIndex = createIndex(List(work))
        val outputIndex = mutable.Map.empty[String, Work[Identified]]
        val memoryDownstream = new MemorySNSDownstream()

        withIdMinterStepFunctionLambda(
          identifiersTableConfig,
            inputIndex,
            outputIndex,
            memoryDownstream
        ) { lambda =>
          eventuallyTableExists(identifiersTableConfig)
          val request = StepFunctionMintingRequest(
            List(work.sourceIdentifier.toString, missingId),
            "test-job"
          )

          val result = lambda.processRequest(request).futureValue

          result.successes should contain only work.sourceIdentifier.toString
          result.failures.map(_.sourceIdentifier) should contain only missingId
          result.failures.foreach { f =>
            f.error should include (missingId)
          }
          result.jobId shouldBe "test-job"

          outputIndex should have size 1
          val canonicalId = outputIndex.keys.head
          memoryDownstream.msgSender.messages.map(_.body) should contain only canonicalId
        }
      }
    }
  }
}
