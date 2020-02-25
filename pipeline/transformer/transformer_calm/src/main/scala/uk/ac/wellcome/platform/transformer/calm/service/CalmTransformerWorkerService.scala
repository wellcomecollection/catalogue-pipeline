package uk.ac.wellcome.platform.transformer.calm.service

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import akka.{Done, NotUsed}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSStream}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.calm.{
  CalmTransformer,
  CalmWorker,
  Worker
}
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
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
  val ec: ExecutionContext,
  materializer: ActorMaterializer)
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
