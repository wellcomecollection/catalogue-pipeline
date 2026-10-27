package weco.pipeline.merger

import weco.lambda.{
  ApplicationConfig,
  Downstream,
  SQSBatchResponseLambdaApp,
  SQSLambdaMessage,
  SQSLambdaMessageFailedRetryable,
  SQSLambdaMessageProcessed,
  SQSLambdaMessageResult
}
import weco.pipeline.matcher.models.MatcherResult
import weco.pipeline.merger.Main.WorkOrImage
import weco.pipeline_storage.Indexable

import scala.concurrent.Future
import scala.util.Try

trait MergerSQSLambda[Config <: ApplicationConfig]
    extends SQSBatchResponseLambdaApp[MatcherResult, Config] {

  protected val mergeProcessor: MergeProcessor
  protected val imageMsgSender: Downstream

  // Index a whole batch's merged works and images in one call rather than one
  // per matcher result. Off by default; serverless pays a fixed latency per request.
  protected val batchIndexing: Boolean = false

  private def notifyDownstream(workOrImage: WorkOrImage): Try[Unit] =
    workOrImage match {
      case Left(work)   => Try(())
      case Right(image) => imageMsgSender.notify(image.id)
    }

  override def processMessages(
    messages: Seq[SQSLambdaMessage[MatcherResult]]
  ): Future[Seq[SQSLambdaMessageResult]] =
    if (batchIndexing) processBatched(messages) else processEach(messages)

  private def docKey(doc: WorkOrImage): (Boolean, String) =
    (doc.isLeft, implicitly[Indexable[WorkOrImage]].id(doc))

  private def retryable(
    messageId: String,
    message: MatcherResult,
    cause: Throwable
  ): SQSLambdaMessageResult =
    SQSLambdaMessageFailedRetryable(
      messageId = messageId,
      // The handler logs only the message, so carry the cause's text in it
      error =
        new Error(s"Failed to merge: ${cause.getMessage}: $message", cause)
    )

  private def processBatched(
    messages: Seq[SQSLambdaMessage[MatcherResult]]
  ): Future[Seq[SQSLambdaMessageResult]] =
    // A merge that fails retries only its own message; the rest index together.
    Future
      .sequence(messages.map {
        case SQSLambdaMessage(messageId, message) =>
          mergeProcessor
            .merge(message)
            .map[Either[SQSLambdaMessageResult, (String, List[WorkOrImage])]](
              docs => Right(messageId -> docs)
            )
            .recover {
              case e => Left(retryable(messageId, message, e))
            }
      })
      .flatMap {
        outcomes =>
          val failedMerges = outcomes.collect { case Left(failure) => failure }
          val merged = outcomes.collect { case Right(m) => m }
          val docs = merged.flatMap(_._2)
          val indexed =
            if (docs.isEmpty) Future.successful(Right(Nil))
            else mergeProcessor.index(docs)
          indexed.map {
            result =>
              val failed = result match {
                case Left(failedDocs) => failedDocs.map(docKey).toSet
                case Right(_)         => Set.empty[(Boolean, String)]
              }
              failedMerges ++ merged.map {
                case (messageId, messageDocs) =>
                  if (messageDocs.exists(doc => failed.contains(docKey(doc)))) {
                    SQSLambdaMessageFailedRetryable(
                      messageId = messageId,
                      error = new Error(
                        s"Failed to index merged documents for $messageId"
                      )
                    )
                  } else {
                    messageDocs.map(notifyDownstream)
                    SQSLambdaMessageProcessed(messageId)
                  }
              }
          } recover {
            // Every merged message has documents in the failed call, so retry each
            case e =>
              failedMerges ++ merged.map {
                case (messageId, _) =>
                  SQSLambdaMessageFailedRetryable(
                    messageId = messageId,
                    error = new Error(
                      s"Failed to index merged documents for $messageId: ${e.getMessage}",
                      e
                    )
                  )
              }
          }
      }

  private def processEach(
    messages: Seq[SQSLambdaMessage[MatcherResult]]
  ): Future[Seq[SQSLambdaMessageResult]] = {

    Future.sequence(messages.map {
      case SQSLambdaMessage(messageId, message) =>
        mergeProcessor
          .process(message)
          .map {
            mergeProcessorResponse =>
              if (mergeProcessorResponse.failures.isEmpty) {
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
          // A merge that throws retries only its own message, not the batch
          .recover {
            case e => retryable(messageId, message, e)
          }
    })
  }
}
