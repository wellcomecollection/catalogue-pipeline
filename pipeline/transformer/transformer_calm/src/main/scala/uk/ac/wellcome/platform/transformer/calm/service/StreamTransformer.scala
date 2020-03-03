package uk.ac.wellcome.platform.transformer.calm.service

import akka.Done
import akka.stream.scaladsl.Flow
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveDecoder
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.transformer.calm.TransformerWorker
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord

import scala.concurrent.{ExecutionContext, Future}

trait StreamTransformer[In, Sender] extends Runnable {
  val worker: TransformerWorker[In, Sender]
}

class SQSStreamTransformer(stream: SQSStream[NotificationMessage],
                           worker: TransformerWorker[CalmRecord, SNSConfig])(
  implicit
  val ec: ExecutionContext)
    extends Runnable {

  implicit val notificationMessageDecoder: Decoder[NotificationMessage] =
    deriveDecoder

  def run(): Future[Done] =
    stream.runStream(
      "CalmTransformerWorkerService",
      source => {
        val end = source.via(Flow.fromFunction(message => message._1))
        val processed = worker.withSource(source)

        processed.flatMapConcat(_ => end)
      }
    )
}
