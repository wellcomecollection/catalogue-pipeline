package weco.pipeline.merger

import weco.lambda.{ApplicationConfig, Downstream, SQSBatchResponseLambdaApp, SQSLambdaMessage, SQSLambdaMessageFailedRetryable, SQSLambdaMessageProcessed, SQSLambdaMessageResult}
import weco.pipeline.matcher.models.MatcherResult
import weco.pipeline.merger.services.WorkRouter
import weco.pipeline.merger.Main.WorkOrImage

import scala.concurrent.Future
import scala.util.Try

trait MergerSQSLambda[Config <: ApplicationConfig]
    extends SQSBatchResponseLambdaApp[MatcherResult, Config] {

  protected val mergeProcessor: MergeProcessor
  protected val workRouter: WorkRouter
  protected val imageMsgSender: Downstream

  private def notifyDownstream(workOrImage: WorkOrImage): Try[Unit] =
    workOrImage match {
      case Left(work)   => workRouter(work)
      case Right(image) => imageMsgSender.notify(image.id)
    }

  override def processMessages(
    messages: Seq[SQSLambdaMessage[MatcherResult]]
  ): Future[Seq[SQSLambdaMessageResult]] = {

    Future.sequence(messages.map {
      case SQSLambdaMessage(messageId, message) =>
        mergeProcessor.process(message).map { mergeProcessorResponse =>
          if(mergeProcessorResponse.failures.isEmpty) {
            // If there are no failures, we notify the downstream services
            mergeProcessorResponse.successes.map(notifyDownstream)
            SQSLambdaMessageProcessed(messageId)
          } else {
            // If there are any failures, we retry the message
            SQSLambdaMessageFailedRetryable(
              messageId = messageId,
              error = new Error(s"Failed to merge: $message")
            )
          }
        }
    })
  }
}
