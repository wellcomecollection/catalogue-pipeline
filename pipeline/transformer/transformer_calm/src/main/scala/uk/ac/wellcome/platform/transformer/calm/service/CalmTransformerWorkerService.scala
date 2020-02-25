package uk.ac.wellcome.platform.transformer.calm.service

import akka.stream.scaladsl.Flow
import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSStream}
import uk.ac.wellcome.platform.transformer.calm.CalmWorker
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

sealed trait CalmTransformerWorkerError extends Throwable
object DecodeKeyError extends CalmTransformerWorkerError
object StoreReadError extends CalmTransformerWorkerError
object TransformerError extends CalmTransformerWorkerError
object MessageSendError extends CalmTransformerWorkerError

class CalmTransformerWorkerService(
  stream: SQSStream[NotificationMessage],
  worker: CalmWorker[SQSConfig]
)(implicit
  val ec: ExecutionContext)
    extends Runnable {
  type Result[T] = Either[Throwable, T]
  type StoreKey = Version[String, Int]

  def run(): Future[Done] =
    stream.runStream(
      "CalmTransformerWorkerService",
      source => {
        val end = source.via(Flow.fromFunction(message => message._1))
        val processed = worker.processMessage(source)

        processed.flatMapConcat(_ => end)
      }
    )
}
