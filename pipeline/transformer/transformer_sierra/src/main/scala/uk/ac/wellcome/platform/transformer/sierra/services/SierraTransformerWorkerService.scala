package uk.ac.wellcome.platform.transformer.sierra.services

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformableTransformer
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.transformer.common.worker.{Transformer, TransformerWorker}
import uk.ac.wellcome.typesafe.Runnable

class SierraTransformerWorkerService[MsgDestination](
  val stream: SQSStream[NotificationMessage],
  val sender: MessageSender[MsgDestination],
  val store: VersionedStore[String, Int, SierraTransformable],
) extends Runnable
    with TransformerWorker[SierraTransformable, MsgDestination] {

  override val transformer: Transformer[SierraTransformable] =
    (input: SierraTransformable, version: Int) =>
      SierraTransformableTransformer.apply(input, version).toEither
}
