package weco.pipeline.id_minter.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class StepFunctionMintingRequest(
  sourceIdentifiers: List[String],
  jobId: Option[String] = None
) {
  def validate: Either[String, StepFunctionMintingRequest] = {
    if (sourceIdentifiers.isEmpty) {
      Left("sourceIdentifiers cannot be empty")
    } else if (sourceIdentifiers.exists(_.trim.isEmpty)) {
      Left("sourceIdentifiers cannot contain empty strings")
    } else if (sourceIdentifiers.size > StepFunctionMintingRequest.MaxBatchSize) {
      Left(s"sourceIdentifiers cannot contain more than ${StepFunctionMintingRequest.MaxBatchSize} items")
    } else {
      Right(this)
    }
  }
}

object StepFunctionMintingRequest {
  val MaxBatchSize: Int = 100

  implicit val decoder: Decoder[StepFunctionMintingRequest] = deriveDecoder
  implicit val encoder: Encoder[StepFunctionMintingRequest] = deriveEncoder
}

case class StepFunctionMintingResponse(
  successes: List[String], // Just the source identifiers that succeeded
  failures: List[StepFunctionMintingFailure],
  jobId: Option[String] = None
)

object StepFunctionMintingResponse {
  implicit val decoder: Decoder[StepFunctionMintingResponse] = deriveDecoder
  implicit val encoder: Encoder[StepFunctionMintingResponse] = deriveEncoder

  def fromMintingResponse(
    mintingResponse: weco.pipeline.id_minter.MintingResponse,
    originalSourceIds: List[String],
    jobId: Option[String]
  ): StepFunctionMintingResponse = {
    // The MintingResponse.successes contains canonical IDs, but we need source IDs
    // We can determine successful source IDs by excluding the failed ones
    val failedSourceIds = mintingResponse.failures.toSet
    val successfulSourceIds = originalSourceIds.filterNot(failedSourceIds.contains)

    val failures = mintingResponse.failures.map { sourceId =>
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