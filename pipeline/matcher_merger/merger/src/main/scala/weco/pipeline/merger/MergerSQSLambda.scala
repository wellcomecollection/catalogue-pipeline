package weco.pipeline.merger

import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.lambda.{ApplicationConfig, Downstream, SQSBatchResponseLambdaApp, SQSLambdaMessage, SQSLambdaMessageFailedRetryable, SQSLambdaMessageResult}
import weco.pipeline_storage.Indexable.imageIndexable

import scala.concurrent.Future
import scala.util.Try

trait MergerSQSLambda[Config <: ApplicationConfig]
  extends SQSBatchResponseLambdaApp[String, Config] {

  protected val processor: MergeProcessor

  override def processMessages(
  messages: Seq[SQSLambdaMessage[String]]
): Future[Seq[SQSLambdaMessageResult]] = {

    type WorkOrImage = Either[Work[Merged], Image[Initial]]

    val messagesMap: Map[String, String] = messages.map {
      message =>
        message.message -> message.messageId
    }.toMap

    // keySet is used to remove duplicates
    processor.process(messagesMap.keySet.toList).map {
      mergerResponse =>
        MergerResponse.successes.map(_: WorkOrImage =>
          sendWorkOrImageSender(_)
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
