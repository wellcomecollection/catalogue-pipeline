package weco.pipeline.id_minter.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class StepFunctionMintingRequest(
  sourceIdentifiers: List[String],
  jobId: String
) {
  def validate: Either[String, StepFunctionMintingRequest] = {
    if (sourceIdentifiers.exists(_.trim.isEmpty))
      Left("sourceIdentifiers cannot contain empty strings")
    else if (jobId.trim.isEmpty)
      Left("jobId cannot be empty")
    else
      Right(this)
  }
}

object StepFunctionMintingRequest {
  implicit val decoder: Decoder[StepFunctionMintingRequest] = deriveDecoder
  implicit val encoder: Encoder[StepFunctionMintingRequest] = deriveEncoder
}

case class StepFunctionMintingResponse(
  successes: List[String], // Just the source identifiers that succeeded
  failures: List[StepFunctionMintingFailure],
  jobId: String
)

object StepFunctionMintingResponse {
  implicit val decoder: Decoder[StepFunctionMintingResponse] = deriveDecoder
  implicit val encoder: Encoder[StepFunctionMintingResponse] = deriveEncoder

  def fromMintingResponse(
    mintingResponse: weco.pipeline.id_minter.MintingResponse,
    originalSourceIds: List[String],
    jobId: String
  ): StepFunctionMintingResponse = {
    // The MintingResponse.successes contains canonical IDs, but we need source IDs
    // We can determine successful source IDs by excluding the failed ones
    val failedSourceIds = mintingResponse.failures.toSet
    val successfulSourceIds =
      originalSourceIds.filterNot(failedSourceIds.contains)

    val failures = mintingResponse.failures.map {
      sourceId =>
        StepFunctionMintingFailure(sourceId, s"Failed to mint ID for $sourceId")
    }.toList

    StepFunctionMintingResponse(successfulSourceIds, failures, jobId)
  }
}

case class StepFunctionMintingFailure(
  sourceIdentifier: String,
  error: String
)

object StepFunctionMintingFailure {
  implicit val decoder: Decoder[StepFunctionMintingFailure] = deriveDecoder
  implicit val encoder: Encoder[StepFunctionMintingFailure] = deriveEncoder
}
