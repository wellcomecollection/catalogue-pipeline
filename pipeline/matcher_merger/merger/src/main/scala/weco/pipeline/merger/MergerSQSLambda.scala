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

  private def processBatched(
    messages: Seq[SQSLambdaMessage[MatcherResult]]
  ): Future[Seq[SQSLambdaMessageResult]] =
    // A merge that fails fails the whole batch, as it does in processEach.
    Future
      .sequence(messages.map {
        case SQSLambdaMessage(messageId, message) =>
          mergeProcessor.merge(message).map(messageId -> _)
      })
      .flatMap {
        merged =>
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
              merged.map {
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
          }
      }

  private def processEach(
    messages: Seq[SQSLambdaMessage[MatcherResult]]
  ): Future[Seq[SQSLambdaMessageResult]] = {

    Future.sequence(messages.map {
      case SQSLambdaMessage(messageId, message) =>
        mergeProcessor.process(message).map {
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
    })
  }
}
