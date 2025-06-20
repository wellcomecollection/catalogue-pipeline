package weco.pipeline.merger

import weco.lambda.{ApplicationConfig, Downstream, SQSBatchResponseLambdaApp, SQSLambdaMessage, SQSLambdaMessageFailedRetryable, SQSLambdaMessageResult}
import weco.pipeline.merger.services.WorkRouter
import weco.pipeline.merger.Main.WorkOrImage
import scala.concurrent.Future
import scala.util.Try


trait MergerSQSLambda[Config <: ApplicationConfig]
  extends SQSBatchResponseLambdaApp[String, Config] {

  protected val mergeProcessor: MergeProcessor
  protected val workRouter: WorkRouter
  protected val imageMsgSender: Downstream

  private def notifyDownstream(workOrImage: WorkOrImage): Try[Unit] =
    workOrImage match {
      case Left(work) => workRouter(work)
      case Right(image) => imageMsgSender.notify(image.id)
    }

  override def processMessages(
  messages: Seq[SQSLambdaMessage[String]]
): Future[Seq[SQSLambdaMessageResult]] = {

    val messagesMap: Map[String, String] = messages.map {
      message =>
        message.message -> message.messageId
    }.toMap

    // keySet is used to remove duplicates
    mergeProcessor.process(messagesMap.keySet.toList).map {
      mergeProcessorResponse =>
        mergeProcessorResponse.successes.map(notifyDownstream) // what if Nil?
        mergeProcessorResponse.failures.map {
          case Left(work) => SQSLambdaMessageFailedRetryable(
            messageId = messagesMap(work.sourceIdentifier.toString),
            error = new Error(s"Failed to merge $work.sourceIdentifier.")
          )
          case Right(image) => SQSLambdaMessageFailedRetryable(
            messageId = messagesMap(image.sourceIdentifier.toString),
            error = new Error(s"Failed to merge $image.sourceIdentifier.")
          )
        }
    }

  }
}
