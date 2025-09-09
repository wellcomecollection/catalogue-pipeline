package weco.pipeline.id_minter

import weco.lambda.{ApplicationConfig, StepFunctionLambdaApp}
import weco.pipeline.id_minter.models.{
  StepFunctionMintingFailure,
  StepFunctionMintingRequest,
  StepFunctionMintingResponse
}

import scala.concurrent.Future

trait IdMinterStepFunctionLambda[Config <: ApplicationConfig]
    extends StepFunctionLambdaApp[
      StepFunctionMintingRequest,
      StepFunctionMintingResponse,
      Config
    ] {

  protected val processor: MintingRequestProcessor

  override def processRequest(
    input: StepFunctionMintingRequest
  ): Future[StepFunctionMintingResponse] = {

    // Validate input first
    input.validate match {
      case Left(validationError) =>
        // Return validation error as a failure response
        val response = StepFunctionMintingResponse(
          successes = List.empty,
          failures = List(
            StepFunctionMintingFailure(
              sourceIdentifier = "",
              error = s"Invalid input: $validationError"
            )
          ),
          jobId = input.jobId
        )
        Future.successful(response)

      case Right(validInput) =>
        // Process using the existing MintingRequestProcessor
        processor.process(validInput.sourceIdentifiers).map {
          mintingResponse =>
            StepFunctionMintingResponse.fromMintingResponse(
              mintingResponse,
              validInput.sourceIdentifiers,
              validInput.jobId
            )
        }
    }
  }
}
