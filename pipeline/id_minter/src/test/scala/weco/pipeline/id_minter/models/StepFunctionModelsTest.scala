package weco.pipeline.id_minter.models

import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.id_minter.MintingResponse

class StepFunctionModelsTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators {

  describe("StepFunctionMintingRequest") {
    it("serializes to and from JSON correctly") {
      val request = StepFunctionMintingRequest(
        sourceIdentifiers = List("sierra-123", "miro-456"),
        jobId = "test-job-001"
      )

      val json = request.asJson
      val decoded = decode[StepFunctionMintingRequest](json.noSpaces)

      decoded shouldBe Right(request)
    }

    describe("validation") {
      it("accepts valid requests") {
        val request = StepFunctionMintingRequest(
          sourceIdentifiers = List("sierra-123", "miro-456"),
          jobId = "job-1"
        )

        request.validate shouldBe Right(request)
      }

      it("accepts empty sourceIdentifiers (no work to do)") {
        val request = StepFunctionMintingRequest(
          sourceIdentifiers = List.empty,
          jobId = "job-1"
        )
        request.validate shouldBe Right(request)
      }

      it("rejects empty string identifiers") {
        val request = StepFunctionMintingRequest(
          sourceIdentifiers = List("sierra-123", "", "miro-456"),
          jobId = "job-1"
        )

        request.validate shouldBe Left(
          "sourceIdentifiers cannot contain empty strings"
        )
      }

      it("rejects whitespace-only identifiers") {
        val request = StepFunctionMintingRequest(
          sourceIdentifiers = List("sierra-123", "   ", "miro-456"),
          jobId = "job-1"
        )

        request.validate shouldBe Left(
          "sourceIdentifiers cannot contain empty strings"
        )
      }

      // Removed: batch size validation tests no longer applicable

      it("rejects empty jobId") {
        val request = StepFunctionMintingRequest(
          sourceIdentifiers = List("sierra-123"),
          jobId = "   "
        )
        request.validate shouldBe Left("jobId cannot be empty")
      }
    }
  }

  describe("StepFunctionMintingResponse") {
    it("serializes to and from JSON correctly") {
      val response = StepFunctionMintingResponse(
        successes = List("sierra-123", "miro-456"),
        failures = List(
          StepFunctionMintingFailure(
            "calm-789",
            "Failed to mint ID for calm-789"
          )
        ),
        jobId = "test-job-001"
      )

      val json = response.asJson
      val decoded = decode[StepFunctionMintingResponse](json.noSpaces)

      decoded shouldBe Right(response)
    }

    // Removed: response jobId now mandatory

    describe("fromMintingResponse") {
      it("creates response from successful minting") {
        val sourceIds = List("sierra-123", "miro-456")
        val mintingResponse = MintingResponse(
          successes = Seq(
            "canonical-id-1",
            "canonical-id-2"
          ), // Canonical IDs don't matter for response
          failures = Seq.empty
        )

        val result = StepFunctionMintingResponse.fromMintingResponse(
          mintingResponse,
          sourceIds,
          "test-job"
        )

        result.successes should have size 2
        result.successes should contain allOf ("sierra-123", "miro-456")
        result.failures shouldBe empty
        result.jobId shouldBe "test-job"
      }

      it("creates response with failures") {
        val sourceIds = List("sierra-123", "sierra-failed-456")
        val failedSourceId = "sierra-failed-456"

        val mintingResponse = MintingResponse(
          successes = Seq("canonical-id-1"),
          failures = Seq(failedSourceId)
        )

        val result = StepFunctionMintingResponse.fromMintingResponse(
          mintingResponse,
          sourceIds,
          "test-job"
        )

        result.successes should have size 1
        result.successes should contain("sierra-123")
        result.failures should have size 1
        result.failures should contain(
          StepFunctionMintingFailure(
            failedSourceId,
            s"Failed to mint ID for $failedSourceId"
          )
        )
        result.jobId shouldBe "test-job"
      }

      it("handles mixed success and failure") {
        val sourceIds =
          List("sierra-123", "sierra-failed-456", "miro-failed-789")
        val failedSourceIds = List("sierra-failed-456", "miro-failed-789")

        val mintingResponse = MintingResponse(
          successes = Seq("canonical-id-1"),
          failures = failedSourceIds
        )

        val result = StepFunctionMintingResponse.fromMintingResponse(
          mintingResponse,
          sourceIds,
          "test-job"
        )

        result.successes should have size 1
        result.successes should contain("sierra-123")
        result.failures should have size 2
        result.jobId shouldBe "test-job"
      }
    }
  }

  describe("StepFunctionMintingFailure") {
    it("serializes to and from JSON correctly") {
      val failure =
        StepFunctionMintingFailure("sierra-123", "Failed to mint ID")

      val json = failure.asJson
      val decoded = decode[StepFunctionMintingFailure](json.noSpaces)

      decoded shouldBe Right(failure)
    }
  }
}
