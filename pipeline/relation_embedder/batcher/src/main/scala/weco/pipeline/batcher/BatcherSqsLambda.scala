package weco.pipeline.batcher

import weco.lambda.{
  ApplicationConfig,
  Downstream,
  SQSBatchResponseLambdaApp,
  SQSLambdaMessage,
  SQSLambdaMessageFailedRetryable,
  SQSLambdaMessageResult
}
import weco.pipeline.batcher.models.Batch
import scala.concurrent.Future

trait BatcherSqsLambda[Config <: ApplicationConfig]
    extends SQSBatchResponseLambdaApp[String, Config] {

  protected val processor: PathsProcessor
  protected val downstream: Downstream

  override def processMessages(
    messages: Seq[SQSLambdaMessage[String]]
  ): Future[Seq[SQSLambdaMessageResult]] = {

    val messagesMap: Map[String, String] = messages.map {
      message =>
        message.message -> message.messageId
    }.toMap

    processor.process(messagesMap.keySet.toList).map {
      batcherResponse =>
        batcherResponse.successes.map(
          downstream.notify(_)(Batch.encoder)
        )

        batcherResponse.failures.map {
          failedPath =>
            SQSLambdaMessageFailedRetryable(
              messageId = messagesMap(failedPath),
              error = new Error(s"Failed to batch path: $failedPath.toString.")
            )
        }
    }
  }
}
