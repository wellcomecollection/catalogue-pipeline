package weco.pipeline.id_minter

import weco.lambda.{
  ApplicationConfig,
  Downstream,
  SQSBatchResponseLambdaApp,
  SQSLambdaMessage,
  SQSLambdaMessageFailedRetryable,
  SQSLambdaMessageResult
}

import scala.concurrent.Future

trait IdMinterSqsLambda[Config <: ApplicationConfig]
    extends SQSBatchResponseLambdaApp[String, Config] {

  protected val processor: MintingRequestProcessor
  protected val downstream: Downstream

  override def processMessages(
    messages: Seq[SQSLambdaMessage[String]]
  ): Future[Seq[SQSLambdaMessageResult]] = {

    // Map the message body to the message ID, as the MintingRequestProcessor
    // expects whole body as an id as input, and returns the same id as output
    // when it fails.
    val messagesMap: Map[String, String] = messages.map {
      message =>
        message.message -> message.messageId
    }.toMap

    // keySet is used to remove duplicates
    processor.process(messagesMap.keySet.toList).map {
      mintingResponse =>
        mintingResponse.successes.map(
          downstream.notify
        )

        mintingResponse.failures.map {
          sourceIdentifier =>
            SQSLambdaMessageFailedRetryable(
              messageId = messagesMap(sourceIdentifier),
              error = new Error(s"Failed to mint ID for $sourceIdentifier.")
            )
        }
    }
  }
}
