package uk.ac.wellcome.platform.transformer.sierra.services

import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformableTransformer
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.transformer.common.worker.TransformerWorker

class SierraTransformerWorkerService[MsgDestination](
                                                             val stream: SQSStream[NotificationMessage],
                                                             val sender: BigMessageSender[MsgDestination, TransformedBaseWork],
                                                             val store: VersionedStore[String, Int, SierraTransformable],
) extends Runnable with TransformerWorker[SierraTransformable, MsgDestination]{

      override val transformer = (input: SierraTransformable, version: Int) => SierraTransformableTransformer.apply(input, version).toEither
}
