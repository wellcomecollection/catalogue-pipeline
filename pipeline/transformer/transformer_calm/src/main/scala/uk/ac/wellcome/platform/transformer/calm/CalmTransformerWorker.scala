package uk.ac.wellcome.platform.transformer.calm

import akka.stream.ActorMaterializer
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.ExecutionContext

class CalmTransformerWorker(
  val stream: SQSStream[NotificationMessage],
  val sender: BigMessageSender[SNSConfig, TransformedBaseWork],
  val store: VersionedStore[String, Int, CalmRecord],
)(implicit val ec: ExecutionContext, val materializer: ActorMaterializer)
    extends Runnable
    with TransformerWorker[CalmRecord, SNSConfig] {
  val transformer = CalmTransformer
}
